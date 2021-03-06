/*
 * Copyright 2012 - 2016 Splice Machine, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.splicemachine.derby.stream.output.update;

import com.splicemachine.SpliceKryoRegistry;
import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.services.io.FormatableBitSet;
import com.splicemachine.db.iapi.sql.execute.ExecRow;
import com.splicemachine.db.iapi.types.DataValueDescriptor;
import com.splicemachine.derby.utils.DerbyBytesUtil;
import com.splicemachine.derby.utils.marshall.EntryDataHash;
import com.splicemachine.derby.utils.marshall.dvd.DescriptorSerializer;
import com.splicemachine.encoding.MultiFieldEncoder;
import com.splicemachine.storage.EntryEncoder;
import com.carrotsearch.hppc.BitSet;

/*
* Entity for encoding rows when primary keys have not been modified
*/
public class NonPkRowHash extends EntryDataHash {
    private final FormatableBitSet finalHeapList;

    public NonPkRowHash(int[] keyColumns,
                        boolean[] keySortOrder,
                        DescriptorSerializer[] serializers,
                        FormatableBitSet finalHeapList) {
        super(keyColumns, keySortOrder,serializers);
        this.finalHeapList = finalHeapList;
    }

    @Override
    protected void pack(MultiFieldEncoder encoder, ExecRow currentRow) throws StandardException {
        encoder.reset();
        DataValueDescriptor[] dvds = currentRow.getRowArray();
        for(int i=finalHeapList.anySetBit();i>=0;i=finalHeapList.anySetBit(i)){
            int position = keyColumns[i];
            DataValueDescriptor dvd = dvds[position];
            DescriptorSerializer serializer = serializers[position];
            serializer.encode(encoder,dvd,false);
        }
    }

    @Override
    protected EntryEncoder buildEntryEncoder() {
        BitSet notNullFields = new BitSet(finalHeapList.size());
        BitSet scalarFields = new BitSet();
        BitSet floatFields = new BitSet();
        BitSet doubleFields = new BitSet();
        for(int i=finalHeapList.anySetBit();i>=0;i=finalHeapList.anySetBit(i)){
            notNullFields.set(i - 1);
            DataValueDescriptor dvd = currentRow.getRowArray()[keyColumns[i]];
            if(DerbyBytesUtil.isScalarType(dvd, null)){
                scalarFields.set(i-1);
            }else if(DerbyBytesUtil.isFloatType(dvd)){
                floatFields.set(i-1);
            }else if(DerbyBytesUtil.isDoubleType(dvd)){
                doubleFields.set(i-1);
            }
        }
        return EntryEncoder.create(SpliceKryoRegistry.getInstance(),currentRow.nColumns(),
                notNullFields,scalarFields,floatFields,doubleFields);
    }

    @Override
    protected BitSet getNotNullFields(ExecRow row, BitSet notNullFields) {
        notNullFields.clear();
        for(int i=finalHeapList.anySetBit();i>=0;i=finalHeapList.anySetBit(i)){
            notNullFields.set(i-1);
        }
        return notNullFields;
    }
}

