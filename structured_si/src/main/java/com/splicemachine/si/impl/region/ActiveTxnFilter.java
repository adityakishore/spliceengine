package com.splicemachine.si.impl.region;

import com.splicemachine.encoding.Encoding;
import com.splicemachine.encoding.MultiFieldDecoder;
import com.splicemachine.si.api.Txn;
import com.splicemachine.si.impl.TxnUtils;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.filter.FilterBase;
import org.apache.hadoop.hbase.util.Bytes;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Scott Fines
 *         Date: 8/18/14
 */
class ActiveTxnFilter extends FilterBase {
    private final long beforeTs;
    private final long afterTs;
    private final byte[] destinationTable;
    private final byte[] newEncodedDestinationTable;

    private boolean isAlive = false;
    private boolean stateSeen= false;
    private boolean keepAliveSeen = false;
    private boolean destTablesSeen = false;
    private boolean filter = false;
    private boolean isChild = false;
    private boolean committed = false;

    private MultiFieldDecoder fieldDecoder;

    public ActiveTxnFilter(long beforeTs, long afterTs, byte[] destinationTable) {
        this.beforeTs = beforeTs;
        this.afterTs = afterTs;
        this.destinationTable = destinationTable;
        if(destinationTable!=null)
            this.newEncodedDestinationTable = Encoding.encodeBytesUnsorted(destinationTable);
        else
            this.newEncodedDestinationTable = null;
    }

    @Override
    public boolean filterRowKey(byte[] buffer, int offset, int length) {
				/*
				 * Since the transaction id must necessarily be the begin timestamp in both
				 * the old and new format, we can filter transactions out based entirely on the
				 * row key here
				 */
        long txnId = TxnUtils.txnIdFromRowKey(buffer, offset, length);
        boolean withinRange = txnId >= afterTs && txnId <= beforeTs;
        return !withinRange;
    }

    @Override
    public boolean filterRow() {
        if(filter) return true;
        else if(!isChild && committed) return true; //not an active parent
        else if(destinationTable!=null && !destTablesSeen) return true; //txn didn't modify the table
        else return false;
    }

    @Override
    public void reset() {
        isAlive=false;
        stateSeen=false;
        keepAliveSeen = false;
        destTablesSeen = false;
        filter = false;
        isChild=false;
        committed = false;
    }

    @Override
    public ReturnCode filterKeyValue(KeyValue kv) {
        ReturnCode rCode = filterState(kv);
        if(rCode!=ReturnCode.INCLUDE) return rCode;
        rCode = filterKeepAlive(kv);
        if(rCode!=ReturnCode.INCLUDE) return rCode;
        rCode = filterDestinationTable(kv);
        if(rCode!=ReturnCode.INCLUDE) return rCode;
        rCode = filterCommitTimestamp(kv);
        if(rCode!=ReturnCode.INCLUDE) return rCode;

        detectChild(kv);
        return ReturnCode.INCLUDE;
    }

    private ReturnCode filterCommitTimestamp(KeyValue kv) {
        if(matchesQualifier(kv,V2TxnDecoder.GLOBAL_COMMIT_QUALIFIER_BYTES)
                ||matchesQualifier(kv,V1TxnDecoder.OLD_GLOBAL_COMMIT_TIMESTAMP_COLUMN)){
            /*
             * We have a global commit timestamp set. This means that not only are we
             * committed, but we are also (probably) the child of a committed transaction.
             * Thus, we can immediately filter this row out
             */
            committed=true;
            filter = true;
            return ReturnCode.NEXT_ROW;
        }
        //check for a normal commit column
        if(matchesQualifier(kv,V2TxnDecoder.COMMIT_QUALIFIER_BYTES)
                ||matchesQualifier(kv,V1TxnDecoder.OLD_COMMIT_TIMESTAMP_COLUMN)){
                /*
                 * We have a commit column, so we've been committed. However, we could
                 * be a child, so we can't filter immediately here
                 */
            committed = true;
        }
        return ReturnCode.INCLUDE;
    }

    private void detectChild(KeyValue kv) {
        if(matchesQualifier(kv, V2TxnDecoder.DATA_QUALIFIER_BYTES)){
                /*
                 * We want to pick out the parent transaction id in the data, which is the second
                 * field. To do that, we wrap up in a re-used MultiFieldDecoder, skip the first entry,
                 * then read the second
                 */
            if(fieldDecoder==null)
                fieldDecoder = MultiFieldDecoder.create();

            fieldDecoder.set(kv.getBuffer(),kv.getValueOffset(),kv.getValueLength());
            fieldDecoder.skip();
            long pTxnId = fieldDecoder.decodeNextLong();
            isChild = pTxnId>0;
        }else if(matchesQualifier(kv, V1TxnDecoder.OLD_PARENT_COLUMN)){
            isChild = kv.getValueLength() > 0;
        }
    }

    private ReturnCode filterDestinationTable(KeyValue kv) {
        if(destinationTable==null) return ReturnCode.INCLUDE; //no destination table to check
        byte[] compareBytes = null;
        if(matchesQualifier(kv,V2TxnDecoder.DESTINATION_TABLE_QUALIFIER_BYTES)){
            compareBytes = newEncodedDestinationTable;
        }else if(matchesQualifier(kv,V1TxnDecoder.OLD_WRITE_TABLE_COLUMN))
            compareBytes = destinationTable;

        if(compareBytes!=null){
            if(destTablesSeen) return ReturnCode.SKIP;
            destTablesSeen = true;
            if(!Bytes.equals(compareBytes, 0, compareBytes.length, kv.getBuffer(), kv.getValueOffset(), kv.getValueLength())){
                //tables do not match
                filter = true;
                return ReturnCode.NEXT_ROW;
            }
        }
        return ReturnCode.INCLUDE;
    }

    private ReturnCode filterKeepAlive(KeyValue kv) {
        Txn.State adjustedState;
        if(matchesQualifier(kv,V2TxnDecoder.KEEP_ALIVE_QUALIFIER_BYTES)){
            if(keepAliveSeen)
                return ReturnCode.SKIP;
            adjustedState = TxnDecoder.adjustStateForTimeout(Txn.State.ACTIVE, kv, false);
        }else if(matchesQualifier(kv,V1TxnDecoder.OLD_KEEP_ALIVE_COLUMN)){
            if(keepAliveSeen)
                return ReturnCode.SKIP;
            adjustedState = TxnDecoder.adjustStateForTimeout(Txn.State.ACTIVE, kv, true);
        }else{
            return ReturnCode.INCLUDE; //not the keep alive column
        }
        keepAliveSeen = true;
        if(adjustedState!= Txn.State.ACTIVE){
                /*
                 * We've timed out. This COULD mean that we're actually timed out, but only
                 * if the transaction claims to still be active. In fact, we have to do a bit better
                 * and make sure that the transaction hasn't been committed/rolledback
                 */
            isAlive = false;
            if(stateSeen &&!committed){
                filter=true;
                return ReturnCode.NEXT_ROW;
            }else
                return ReturnCode.INCLUDE;
        }else{
            isAlive = true;
            return ReturnCode.INCLUDE;
        }
    }

    private ReturnCode filterState(KeyValue kv) {
        if(committed){
                /*
                 * We've already seen the commit timestamp field, so we know that our state
                 * is committed, no need to check this, just throw it away
                 */
            return ReturnCode.INCLUDE;
        }
        if(matchesQualifier(kv,V2TxnDecoder.STATE_QUALIFIER_BYTES)
                || matchesQualifier(kv,V1TxnDecoder.OLD_STATUS_COLUMN)){
            if(stateSeen) return ReturnCode.SKIP;
            stateSeen = true;
            byte[] checkState = Txn.State.ACTIVE.encode(); //doesn't actually create a new byte[]
            boolean isActive = Bytes.equals(checkState,0,checkState.length,
                    kv.getBuffer(),kv.getValueOffset(),kv.getValueLength());
                /*
                 * Just because a transaction is active doesn't mean that it's actually active--it could
                 * have been rolled back via the transaction timeout mechanism. Thus, we'll need to check to
                 * make sure that the transaction hasn't timed out while we were working on it.
                 */
            if (isActive && keepAliveSeen && !isAlive) {
                //transaction timed out, so filter it
                filter = true;
                return ReturnCode.NEXT_ROW;
            }
            if(!isActive){
                checkState = Txn.State.ROLLEDBACK.encode();
                if(Bytes.equals(checkState,0,checkState.length,
                        kv.getBuffer(),kv.getValueOffset(),kv.getValueLength())){
                    //we are rolled back, don't need to expose it
                    filter = true;
                    return ReturnCode.NEXT_ROW;
                }
                committed = true; //we know we are committed
            }
            return ReturnCode.INCLUDE;
        }else{
            return ReturnCode.INCLUDE; //we can't filter it based on state
        }
    }

    private boolean matchesQualifier(KeyValue kv, byte[] qualifier) {
        return Bytes.equals(qualifier, 0, qualifier.length,
                kv.getBuffer(),kv.getQualifierOffset(), kv.getQualifierLength());
    }


    @Override public void write(DataOutput out) throws IOException {  }
    @Override public void readFields(DataInput in) throws IOException {  }
}