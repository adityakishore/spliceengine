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

package com.splicemachine.derby.stream.function.merge;

import com.splicemachine.derby.impl.sql.execute.operations.*;
import com.splicemachine.derby.stream.iapi.OperationContext;
import com.splicemachine.derby.stream.iterator.merge.AbstractMergeJoinIterator;
import com.splicemachine.derby.stream.iterator.merge.MergeOuterJoinIterator;
import org.sparkproject.guava.collect.PeekingIterator;

/**
 * Created by jleach on 6/9/15.
 */
public class MergeOuterJoinFlatMapFunction extends AbstractMergeJoinFlatMapFunction {
    public MergeOuterJoinFlatMapFunction() {
        super();
    }

    public MergeOuterJoinFlatMapFunction(OperationContext<JoinOperation> operationContext) {
        super(operationContext);
    }

    @Override
    protected AbstractMergeJoinIterator createMergeJoinIterator(PeekingIterator<LocatedRow> leftPeekingIterator, PeekingIterator<LocatedRow> rightPeekingIterator, int[] leftHashKeys, int[] rightHashKeys, JoinOperation mergeJoinOperation, OperationContext<JoinOperation> operationContext) {
        return new MergeOuterJoinIterator(leftPeekingIterator,
                rightPeekingIterator,
                leftHashKeys, rightHashKeys,
                mergeJoinOperation, operationContext);
    }
}
