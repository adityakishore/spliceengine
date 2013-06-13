package com.splicemachine.derby.utils.marshall;

import com.splicemachine.derby.utils.DerbyBytesUtil;
import com.splicemachine.derby.utils.SpliceUtils;
import com.splicemachine.encoding.MultiFieldDecoder;
import com.splicemachine.encoding.MultiFieldEncoder;
import com.splicemachine.tools.ThreadLocalRandom;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.sql.execute.ExecRow;
import org.apache.derby.iapi.types.DataValueDescriptor;

/**
 * @author Scott Fines
 *         Created on: 6/12/13
 */
public enum KeyType implements KeyMarshall{
    /**
     * Prepends a fixed (non-unique) Prefix to the row key,
     * but does nothing for a postfix.
     */
    FIXED_PREFIX {
        @Override
        public void encodeKey(ExecRow row,
                              int[] keyColumns,
                              boolean[] sortOrder,
                              byte[] keyPostfix,
                              MultiFieldEncoder keyEncoder) throws StandardException {
            //the prefix will have already been set in the constructor,
            //so we just delegate to BARE
            BARE.encodeKey(row,keyColumns,sortOrder,keyPostfix,keyEncoder);
        }

        @Override
        public void decode(ExecRow template,
                           int[] reversedKeyColumns,
                           boolean[] sortOrder,
                           MultiFieldDecoder rowDecoder) throws StandardException {
            //throw away the first bytes--treat them as a string
            rowDecoder.decodeNextString();
            BARE.decode(template,reversedKeyColumns,sortOrder,rowDecoder);
        }

        @Override
        public int getFieldCount(int[] keyColumns) {
            return BARE.getFieldCount(keyColumns)+1;
        }
    },
    /**
     * Appends a postfix to the row key, but does nothing with a prefix.
     */
    FIXED_POSTFIX {
        @Override
        public void encodeKey(ExecRow row,
                              int[] keyColumns,
                              boolean[] sortOrder,
                              byte[] keyPostfix,
                              MultiFieldEncoder keyEncoder) throws StandardException {
            //delegate to BARE, then append the postfix
            BARE.encodeKey(row,keyColumns,sortOrder,keyPostfix,keyEncoder);
            keyEncoder.encodeNextUnsorted(keyPostfix);
        }

        @Override
        public void decode(ExecRow template,
                           int[] reversedKeyColumns,
                           boolean[] sortOrder,
                           MultiFieldDecoder rowDecoder) throws StandardException {
            BARE.decode(template,reversedKeyColumns,sortOrder,rowDecoder);
        }

        @Override
        public int getFieldCount(int[] keyColumns) {
            return BARE.getFieldCount(keyColumns)+1;
        }
    },
    UNIQUE_POSTFIX {
        @Override
        public void encodeKey(ExecRow row, int[] keyColumns, boolean[] sortOrder, byte[] keyPostfix, MultiFieldEncoder keyEncoder) throws StandardException {
            BARE.encodeKey(row,keyColumns,sortOrder,keyPostfix,keyEncoder);
            //encode the postfix in place
            keyEncoder.encodeNextUnsorted(keyPostfix);
            //encode random bits at the end of the postfix for uniqueness
            keyEncoder.encodeNextUnsorted(SpliceUtils.getUniqueKey());
        }

        @Override
        public void decode(ExecRow template, int[] reversedKeyColumns,
                           boolean[] sortOrder,MultiFieldDecoder rowDecoder) throws StandardException {
            BARE.decode(template,reversedKeyColumns,sortOrder,rowDecoder);
        }

        @Override
        public int getFieldCount(int[] keyColumns) {
            return BARE.getFieldCount(keyColumns)+2;
        }
    },
    /**
     * Prepends a prefix, and appends a postfix to the row key
     */
    FIXED_PREFIX_AND_POSTFIX {
        @Override
        public void encodeKey(ExecRow row,
                              int[] keyColumns,
                              boolean[] sortOrder,
                              byte[] keyPostfix,
                              MultiFieldEncoder keyEncoder) throws StandardException {
            //the prefix is set in the constructor, so this functions
            //the same as POSTFIX_ONLY
            FIXED_POSTFIX.encodeKey(row,keyColumns,sortOrder,keyPostfix,keyEncoder);
        }

        @Override
        public void decode(ExecRow template,
                           int[] reversedKeyColumns,
                           boolean[] sortOrder,
                           MultiFieldDecoder rowDecoder) throws StandardException {
            FIXED_PREFIX.decode(template, reversedKeyColumns, sortOrder,rowDecoder);
        }

        @Override
        public int getFieldCount(int[] keyColumns) {
            return FIXED_POSTFIX.getFieldCount(keyColumns)+1;
        }
    },
    FIXED_PREFIX_UNIQUE_POSTFIX {
        @Override
        public void encodeKey(ExecRow row, int[] keyColumns, boolean[] sortOrder, byte[] keyPostfix, MultiFieldEncoder keyEncoder) throws StandardException {
            UNIQUE_POSTFIX.encodeKey(row,keyColumns,sortOrder,keyPostfix,keyEncoder);
        }

        @Override
        public void decode(ExecRow template, int[] reversedKeyColumns, boolean[] sortOrder,MultiFieldDecoder rowDecoder) throws StandardException {
            FIXED_PREFIX.decode(template, reversedKeyColumns, sortOrder,rowDecoder);
        }

        @Override
        public int getFieldCount(int[] keyColumns) {
            return UNIQUE_POSTFIX.getFieldCount(keyColumns)+1;
        }
    },
    PREFIX_ONLY {
        @Override
        public void encodeKey(ExecRow row, int[] keyColumns, boolean[] sortOrder, byte[] keyPostfix, MultiFieldEncoder keyEncoder) throws StandardException {
            //no-op, prefix is set in the constructor
        }

        @Override
        public void decode(ExecRow template, int[] reversedKeyColumns, boolean[] sortOrder,MultiFieldDecoder rowDecoder) throws StandardException {
            //no-op, no fields are present in the key
        }

        @Override
        public int getFieldCount(int[] keyColumns) {
            return 1;
        }
    },
    PREFIX_FIXED_POSTFIX_ONLY{
        @Override
        public void encodeKey(ExecRow row, int[] keyColumns, boolean[] sortOrder, byte[] keyPostfix, MultiFieldEncoder keyEncoder) throws StandardException {
            keyEncoder.encodeNextUnsorted(keyPostfix);
        }

        @Override
        public void decode(ExecRow template, int[] reversedKeyColumns, boolean[] sortOrder,MultiFieldDecoder rowDecoder) throws StandardException {
            //no-op, no fields are present in the key
        }

        @Override
        public int getFieldCount(int[] keyColumns) {
            return 2;
        }
    },
    PREFIX_UNIQUE_POSTFIX_ONLY{
        @Override
        public void encodeKey(ExecRow row, int[] keyColumns, boolean[] sortOrder, byte[] keyPostfix, MultiFieldEncoder keyEncoder) throws StandardException {
            keyEncoder.encodeNextUnsorted(keyPostfix);
            //add a uniqueness field
            keyEncoder.encodeNextUnsorted(SpliceUtils.getUniqueKey());
        }

        @Override
        public void decode(ExecRow template, int[] reversedKeyColumns,boolean[] sortOrder, MultiFieldDecoder rowDecoder) throws StandardException {
            //no-op, no fields are present in the key
        }

        @Override
        public int getFieldCount(int[] keyColumns) {
            return 3;
        }
    },
    /**
     * Constructs a key from a 4-byte random salt and a UUID
     */
    SALTED {
        @Override
        public void encodeKey(ExecRow row,
                              int[] keyColumns,
                              boolean[] sortOrder,
                              byte[] keyPostfix,
                              MultiFieldEncoder keyEncoder) {
            //salted ignores row, keyColumns,sortOrder, because it generates
            //a row key randomly
            keyEncoder.encodeNext(ThreadLocalRandom.current().nextInt());
            keyEncoder.encodeNextUnsorted(SpliceUtils.getUniqueKey());
        }

        @Override
        public void decode(ExecRow template, int[] reversedKeyColumns,
                           boolean[] sortOrder,MultiFieldDecoder rowDecoder) throws StandardException {
            //no-op--no fields are present in the key
        }

        @Override
        public int getFieldCount(int[] keyColumns) {
            return 2;
        }
    },
    /**
     * No prefix or Postfix is to be used
     */
    BARE {
        @Override
        public void encodeKey(ExecRow row,
                              int[] keyColumns,
                              boolean[] sortOrder,
                              byte[] keyPostfix,
                              MultiFieldEncoder keyEncoder) throws StandardException {
            DataValueDescriptor[] fields = row.getRowArray();
            if(sortOrder!=null){
                for (int keyColumn : keyColumns) {
                    boolean desc = !sortOrder[keyColumn];
                    DataValueDescriptor dvd = fields[keyColumn];
                    if (dvd != null && !dvd.isNull())
                        DerbyBytesUtil.encodeInto(keyEncoder, dvd, desc);
                    else
                        keyEncoder.setRawBytes(null);
                }
            }else{
                for(int key:keyColumns){
                    DataValueDescriptor dvd = fields[key];
                    if(dvd!=null&&!dvd.isNull())
                        DerbyBytesUtil.encodeInto(keyEncoder,dvd,false);
                    else
                        keyEncoder.setRawBytes(null); //we need to fill null items with 0
                }
            }
        }

        @Override
        public void decode(ExecRow template, int[] reversedKeyColumns,
                           boolean[] sortOrder,MultiFieldDecoder rowDecoder) throws StandardException {
            if(sortOrder!=null){
                for (int rowSpot : reversedKeyColumns) {
                    boolean desc = !sortOrder[rowSpot];
                    if (rowSpot != -1) {
                        DerbyBytesUtil.decodeInto(rowDecoder, template.getColumn(rowSpot+1),desc);
                    }
                }
            }else{
                for (int rowSpot : reversedKeyColumns) {
                    if (rowSpot != -1) {
                        DerbyBytesUtil.decodeInto(rowDecoder, template.getColumn(rowSpot+1));
                    }
                }
            }
        }

        @Override
        public int getFieldCount(int[] keyColumns) {
            if(keyColumns==null)
                return 0;
            return keyColumns.length;
        }
    }
}
