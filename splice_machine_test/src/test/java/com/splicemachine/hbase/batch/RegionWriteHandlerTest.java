package com.splicemachine.hbase.batch;

import com.carrotsearch.hppc.ObjectArrayList;
import com.splicemachine.hbase.KVPair;
import com.splicemachine.hbase.MockRegion;
import com.splicemachine.pipeline.writehandler.IndexCallBufferFactory;
import com.splicemachine.si.api.server.TransactionalRegion;
import com.splicemachine.si.api.server.Transactor;
import com.splicemachine.si.impl.txn.ActiveWriteTxn;
import com.splicemachine.si.impl.TxnRegion;
import com.splicemachine.si.api.txn.TxnSupplier;
import com.splicemachine.si.impl.DataStore;
import com.splicemachine.si.impl.readresolve.NoOpReadResolver;
import com.splicemachine.si.impl.rollforward.NoopRollForward;
import com.splicemachine.concurrent.ResettableCountDownLatch;
import com.splicemachine.pipeline.api.Code;
import com.splicemachine.pipeline.client.WriteResult;
import com.splicemachine.pipeline.context.PipelineWriteContext;
import com.splicemachine.pipeline.writehandler.RegionWriteHandler;

import com.splicemachine.si.impl.store.IgnoreTxnCacheSupplier;
import org.apache.hadoop.hbase.HRegionInfo;
import org.apache.hadoop.hbase.client.Mutation;
import org.apache.hadoop.hbase.coprocessor.RegionCoprocessorEnvironment;
import org.apache.hadoop.hbase.regionserver.HRegion;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Scott Fines
 * Created on: 9/25/13
 */
public class RegionWriteHandlerTest {

    @Test
    public void testWritesRowsCorrectly() throws Exception {
        final ObjectArrayList<Mutation> successfulPuts = ObjectArrayList.newInstance();
        HRegion testRegion = MockRegion.getMockRegion(MockRegion.getSuccessOnlyAnswer(successfulPuts));

		TxnSupplier supplier = mock(TxnSupplier.class);
        IgnoreTxnCacheSupplier ignoreTxnCacheSupplier = mock(IgnoreTxnCacheSupplier.class);
				//TODO -sf- make this simpler
        RegionCoprocessorEnvironment env = mock(RegionCoprocessorEnvironment.class);
        when(env.getRegion()).thenReturn(testRegion);
		TransactionalRegion txnRegion = new TxnRegion(testRegion, NoopRollForward.INSTANCE, NoOpReadResolver.INSTANCE,
								supplier,ignoreTxnCacheSupplier,mock(DataStore.class), mock(Transactor.class));
        PipelineWriteContext testContext = new PipelineWriteContext(new IndexCallBufferFactory(),new ActiveWriteTxn(1l,1l),txnRegion, false, env);
        testContext.addLast(new RegionWriteHandler(txnRegion,new ResettableCountDownLatch(0), null));

        ObjectArrayList<KVPair> pairs = ObjectArrayList.newInstance();
        for(int i=0;i<10;i++){
            KVPair next = new KVPair(Bytes.toBytes(i),Bytes.toBytes(i));
            pairs.add(next);
            testContext.sendUpstream(next);
        }

        //make sure that nothing has been written to the region
        Assert.assertEquals("Writes have made it to the region!", 0, successfulPuts.size());

        //finish
        testContext.flush();
        Map<KVPair,WriteResult> finish = testContext.close();

        //make sure that the finish has the correct (successful) WriteResult
        for(WriteResult result:finish.values()){
            Assert.assertEquals("Incorrect result!",Code.SUCCESS,result.getCode());
        }

        //make sure that all the KVs made it to the region
        Assert.assertEquals("incorrect number of rows made it to the region!",pairs.size(),successfulPuts.size());
        Object[] buffer = successfulPuts.buffer;
        for (int i = 0; i< successfulPuts.size(); i++) {
        	Mutation mutation = (Mutation) buffer[i];
            boolean found = false;
            Object[] buffer2 = pairs.buffer;
            for (int j = 0; j< pairs.size(); j++) {
            	KVPair pair = (KVPair) buffer2[j];
                found = Bytes.equals(mutation.getRow(),pair.getRowKey());
                if(found)
                    break;
            }
            Assert.assertTrue("Row "+ Bytes.toHex(mutation.getRow())+" magically appeared",found);
        }
        buffer = pairs.buffer;
        for (int i = 0; i< pairs.size(); i++) {
        	KVPair pair = (KVPair) buffer[i];
            boolean found = false;
            Object[] buffer2 = successfulPuts.buffer;
            for (int j = 0; j< pairs.size(); j++) {
            	Mutation mutation = (Mutation) buffer2[j];
                found = Bytes.equals(mutation.getRow(),pair.getRowKey());
                if(found)
                    break;
            }
            Assert.assertTrue("Row "+ Bytes.toHex(pair.getRowKey())+" magically appeared",found);
        }
    }

    @Test
    public void testNotServingRegionExceptionThrowingCausesAllRowsToFail() throws Exception {
    	ObjectArrayList<Mutation> results = ObjectArrayList.newInstance();

        HRegion testRegion = MockRegion.getMockRegion(MockRegion.getNotServingRegionAnswer());

        RegionCoprocessorEnvironment rce = mock(RegionCoprocessorEnvironment.class);
        when(rce.getRegion()).thenReturn(testRegion);

		TxnSupplier supplier = mock(TxnSupplier.class);
        IgnoreTxnCacheSupplier ignoreTxnCacheSupplier = mock(IgnoreTxnCacheSupplier.class);
				//TODO -sf- make this simpler
		TransactionalRegion txnRegion = new TxnRegion(testRegion, NoopRollForward.INSTANCE, NoOpReadResolver.INSTANCE,
								supplier,ignoreTxnCacheSupplier,mock(DataStore.class), mock(Transactor.class));


        RegionCoprocessorEnvironment env = mock(RegionCoprocessorEnvironment.class);
        when(env.getRegion()).thenReturn(testRegion);
        PipelineWriteContext testContext = new PipelineWriteContext(new IndexCallBufferFactory(),new ActiveWriteTxn(1l,1l),txnRegion, false, env);
        testContext.addLast(new RegionWriteHandler(txnRegion,new ResettableCountDownLatch(0), null));

        ObjectArrayList<KVPair> pairs = ObjectArrayList.newInstance();
        for(int i=0;i<10;i++){
            KVPair next = new KVPair(Bytes.toBytes(i),Bytes.toBytes(i));
            pairs.add(next);
            testContext.sendUpstream(next);
        }

        Map<KVPair, WriteResult> finish = testContext.close();

        //make sure no one got written
        Assert.assertEquals("Some rows got written, even though the region is closed!",0,results.size());
        for(WriteResult result:finish.values()){
            Assert.assertEquals("Row did not return correct code!", Code.NOT_SERVING_REGION,result.getCode());
        }
    }

    @Test
    public void testClosingRegionBeforeSendingUpstreamResultsInNotServingRegionCodes() throws Exception {
    	ObjectArrayList<Mutation> results = ObjectArrayList.newInstance();

        HRegion testRegion = MockRegion.getMockRegion(MockRegion.getSuccessOnlyAnswer(results));
        when(testRegion.isClosed()).thenReturn(true);

		TxnSupplier supplier = mock(TxnSupplier.class);
        IgnoreTxnCacheSupplier ignoreTxnCacheSupplier = mock(IgnoreTxnCacheSupplier.class);
		//TODO -sf- make this simpler
		TransactionalRegion txnRegion = new TxnRegion(testRegion, NoopRollForward.INSTANCE, NoOpReadResolver.INSTANCE,
								supplier,ignoreTxnCacheSupplier,mock(DataStore.class), mock(Transactor.class));

        RegionCoprocessorEnvironment env = mock(RegionCoprocessorEnvironment.class);
        when(env.getRegion()).thenReturn(testRegion);
        PipelineWriteContext testContext = new PipelineWriteContext(new IndexCallBufferFactory(),new ActiveWriteTxn(1l,1l),txnRegion, false, env);
        testContext.addLast(new RegionWriteHandler(txnRegion,new ResettableCountDownLatch(0), null));

        ObjectArrayList<KVPair> pairs = ObjectArrayList.newInstance();
        for(int i=0;i<10;i++){
            KVPair next = new KVPair(Bytes.toBytes(i),Bytes.toBytes(i));
            pairs.add(next);
            testContext.sendUpstream(next);
        }

        Map<KVPair, WriteResult> finish = testContext.close();

        //make sure no one got written
        Assert.assertEquals("Some rows got written, even though the region is closed!",0,results.size());
        for(WriteResult result:finish.values()){
            Assert.assertEquals("Row did not return correct code!", Code.NOT_SERVING_REGION,result.getCode());
        }
    }

    @Test
    public void testWrongRegionIsProperlyReturned() throws Exception {
    	ObjectArrayList<Mutation> results = ObjectArrayList.newInstance();

        HRegion testRegion = MockRegion.getMockRegion(MockRegion.getSuccessOnlyAnswer(results));

        HRegionInfo info = testRegion.getRegionInfo();
        when(info.getEndKey()).thenReturn(Bytes.toBytes(11));

		TxnSupplier supplier = mock(TxnSupplier.class);
        IgnoreTxnCacheSupplier ignoreTxnCacheSupplier = mock(IgnoreTxnCacheSupplier.class);
		//TODO -sf- make this simpler
		TransactionalRegion txnRegion = new TxnRegion(testRegion, NoopRollForward.INSTANCE, NoOpReadResolver.INSTANCE,
								supplier,ignoreTxnCacheSupplier,mock(DataStore.class), mock(Transactor.class));

        RegionCoprocessorEnvironment env = mock(RegionCoprocessorEnvironment.class);
        when(env.getRegion()).thenReturn(testRegion);
        PipelineWriteContext testContext = new PipelineWriteContext(new IndexCallBufferFactory(),new ActiveWriteTxn(1l,1l),txnRegion, false, env);
        testContext.addLast(new RegionWriteHandler(txnRegion,new ResettableCountDownLatch(0), null));

        ObjectArrayList<KVPair> successfulPairs = ObjectArrayList.newInstance();
        for(int i=0;i<10;i++){
            KVPair next = new KVPair(Bytes.toBytes(i),Bytes.toBytes(i));
            successfulPairs.add(next);
            testContext.sendUpstream(next);
        }

        //close the region
        ObjectArrayList<KVPair> failedPairs = ObjectArrayList.newInstance();
        for(int i=11;i<20;i++){
            KVPair next = new KVPair(Bytes.toBytes(i),Bytes.toBytes(i));
            failedPairs.add(next);
            testContext.sendUpstream(next);
        }
        testContext.flush();
        Map<KVPair, WriteResult> finish = testContext.close();

        //make sure the correct number of rows got written
        Assert.assertEquals("Incorrect number of rows written!",successfulPairs.size(),results.size());

        //make sure every correct row shows up in results AND has the correct code
        Object[] buffer = successfulPairs.buffer;
        for (int i =0 ; i<successfulPairs.size(); i++) {
        	KVPair success = (KVPair)buffer[i];
            Assert.assertEquals("Incorrect return code!", Code.SUCCESS,finish.get(success).getCode());
            boolean found = false;
            
            Object[] buffer2 = results.buffer;
            for (int j =0 ; j<results.size(); j++) {
            	Mutation mutation = (Mutation)buffer2[j];
                found = Bytes.equals(mutation.getRow(),success.getRowKey());
                if(found)
                    break;
            }
            Assert.assertTrue("Row not present in results!", found);
        }

        
        //make sure every failed row has good code AND isn't in results
        
        buffer = failedPairs.buffer;
        for (int i =0 ; i<failedPairs.size(); i++) {
        	KVPair failure = (KVPair)buffer[i];
            Assert.assertEquals("Incorrect return code!", Code.WRONG_REGION,finish.get(failure).getCode());
            boolean found = false;
            Object[] buffer2 = results.buffer;
            for (int j =0 ; j<results.size(); j++) {
            	Mutation mutation = (Mutation)buffer2[j];
                found = Bytes.equals(mutation.getRow(), failure.getRowKey());
                if(found)
                    break;
            }
            Assert.assertFalse("Row present in results!",found);
        }
    }

    @Test
    public void testClosingRegionHalfwayThroughUpstreamWritesHalfTheRecords() throws Exception {
    	ObjectArrayList<Mutation> results = ObjectArrayList.newInstance();

        HRegion testRegion = MockRegion.getMockRegion(MockRegion.getSuccessOnlyAnswer(results));

		TxnSupplier supplier = mock(TxnSupplier.class);
        IgnoreTxnCacheSupplier ignoreTxnCacheSupplier = mock(IgnoreTxnCacheSupplier.class);
		//TODO -sf- make this simpler
		TransactionalRegion txnRegion = new TxnRegion(testRegion, NoopRollForward.INSTANCE, NoOpReadResolver.INSTANCE,
								supplier,ignoreTxnCacheSupplier,mock(DataStore.class), mock(Transactor.class));
        RegionCoprocessorEnvironment env = mock(RegionCoprocessorEnvironment.class);
        when(env.getRegion()).thenReturn(testRegion);
        PipelineWriteContext testContext = new PipelineWriteContext(new IndexCallBufferFactory(),new ActiveWriteTxn(1l,1l),txnRegion, false, env);
        testContext.addLast(new RegionWriteHandler(txnRegion,new ResettableCountDownLatch(0), null));

        ObjectArrayList<KVPair> successfulPairs = ObjectArrayList.newInstance();
        for(int i=0;i<10;i++){
            KVPair next = new KVPair(Bytes.toBytes(i),Bytes.toBytes(i));
            successfulPairs.add(next);
            testContext.sendUpstream(next);
        }

        //close the region
        when(testRegion.isClosing()).thenReturn(true);
        ObjectArrayList<KVPair> failedPairs = ObjectArrayList.newInstance();
        for(int i=11;i<20;i++){
            KVPair next = new KVPair(Bytes.toBytes(i),Bytes.toBytes(i));
            failedPairs.add(next);
            testContext.sendUpstream(next);
        }
        testContext.flush();
        Map<KVPair, WriteResult> finish = testContext.close();

        //make sure the correct number of rows got written
        Assert.assertEquals("Incorrect number of rows written!",successfulPairs.size(),results.size());

        //make sure every correct row shows up in results AND has the correct code
        
        Object[] buffer = successfulPairs.buffer;
        for (int i =0 ; i<successfulPairs.size(); i++) {
        	KVPair success = (KVPair)buffer[i];
            Assert.assertEquals("Incorrect return code!", Code.SUCCESS,finish.get(success).getCode());
            boolean found = false;

            Object[] buffer2 = results.buffer;
            for (int j =0 ; j<results.size(); j++) {
            	Mutation mutation = (Mutation)buffer2[j];
                found = Bytes.equals(mutation.getRow(),success.getRowKey());
                if(found)
                    break;
            }
            Assert.assertTrue("Row not present in results!", found);
        }

        //make sure every failed row has good code AND isn't in results
        
        buffer = failedPairs.buffer;
        for (int i =0 ; i<failedPairs.size(); i++) {
        	KVPair failure = (KVPair)buffer[i];
            Assert.assertEquals("Incorrect return code!", Code.NOT_SERVING_REGION,finish.get(failure).getCode());
            boolean found = false;
            Object[] buffer2 = results.buffer;
            for (int j =0 ; j<results.size(); j++) {
            	Mutation mutation = (Mutation)buffer2[j];
                found = Bytes.equals(mutation.getRow(), failure.getRowKey());
                if(found)
                    break;
            }
            Assert.assertFalse("Row present in results!",found);
        }
    }


}
