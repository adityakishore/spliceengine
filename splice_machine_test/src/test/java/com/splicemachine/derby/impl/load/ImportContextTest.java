package com.splicemachine.derby.impl.load;

import com.splicemachine.homeless.SerializationUtils;
import com.splicemachine.si.api.Txn;
import com.splicemachine.si.impl.ActiveWriteTxn;
import com.splicemachine.si.impl.UnsupportedLifecycleManager;
import com.splicemachine.si.impl.WritableTxn;
import org.junit.Assert;
import org.junit.Test;

public class ImportContextTest {

    @Test
    public void testSerializeImportContext() throws Exception{
        ImportContext.Builder builder = new ImportContext.Builder();
        ImportContext ic = builder.path("/foo")
                                .colDelimiter(",")
                                .destinationTable(1l)
                                .byteOffset(100l)
                                .bytesToRead(10)
                                .build();

        ImportContext newIC = SerializationUtils.roundTripObject(ic);

        Assert.assertEquals("/foo", newIC.getFilePath().toString());
        Assert.assertEquals(",", newIC.getColumnDelimiter());
        Assert.assertEquals(1l, newIC.getTableId());
        Assert.assertEquals(100l, newIC.getByteOffset());
        Assert.assertEquals(10, newIC.getBytesToRead());
    }

    @Test( expected = NullPointerException.class )
    public void testMissingPath() throws Exception{
        ImportContext.Builder builder = new ImportContext.Builder();
        ImportContext ic = builder.colDelimiter(",")
                .destinationTable(1l)
                .byteOffset(100l)
                .bytesToRead(10)
                .build();
    }


    @Test( expected = NullPointerException.class )
    public void testMissingDestinationTable() throws Exception{
        ImportContext.Builder builder = new ImportContext.Builder();
        ImportContext ic = builder.path("/foo")
                .colDelimiter(",")
                .byteOffset(100l)
                .bytesToRead(10)
                .build();
    }
}