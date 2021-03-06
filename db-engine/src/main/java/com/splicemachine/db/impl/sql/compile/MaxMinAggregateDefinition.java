/*
 * Apache Derby is a subproject of the Apache DB project, and is licensed under
 * the Apache License, Version 2.0 (the "License"); you may not use these files
 * except in compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * Splice Machine, Inc. has modified this file.
 *
 * All Splice Machine modifications are Copyright 2012 - 2016 Splice Machine, Inc.,
 * and are licensed to you under the License; you may not use this file except in
 * compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */

package com.splicemachine.db.impl.sql.compile;

import com.splicemachine.db.iapi.reference.ClassName;
import com.splicemachine.db.iapi.services.context.ContextService;
import com.splicemachine.db.iapi.sql.compile.AggregateDefinition;
import com.splicemachine.db.iapi.sql.conn.LanguageConnectionContext;
import com.splicemachine.db.iapi.types.DataTypeDescriptor;
import com.splicemachine.db.iapi.types.TypeId;

/**
 * Defintion for the MAX()/MIN() aggregates.
 *
 */
public class MaxMinAggregateDefinition 
		implements AggregateDefinition
{
	private boolean isMax;
    private boolean isWindowFunction;

	/**
	 * Niladic constructor.  Does nothing.  For ease
	 * Of use, only.
	 */
	public MaxMinAggregateDefinition() { super(); }

	/**
	 * Determines the result datatype.  Accept NumberDataValues
	 * only.  
	 * <P>
	 * <I>Note</I>: In the future you should be able to do
	 * a sum user data types.  One option would be to run
	 * sum on anything that implements divide().  
	 *
	 * @param inputType	the input type, either a user type or a java.lang object
	 *
	 * @return the output Class (null if cannot operate on
	 *	value expression of this type.
	 */
	public final DataTypeDescriptor	getAggregator(DataTypeDescriptor inputType, 
				StringBuffer aggregatorClass) 
	{
		LanguageConnectionContext lcc = (LanguageConnectionContext)
			ContextService.getContext(LanguageConnectionContext.CONTEXT_ID);

			/*
			** MIN and MAX may return null
			*/
		DataTypeDescriptor dts = inputType.getNullabilityType(true);
		TypeId compType = dts.getTypeId();

		/*
		** If the class implements NumberDataValue, then we
		** are in business.  Return type is same as input
		** type.
		*/
		if (compType.orderable(
						lcc.getLanguageConnectionFactory().getClassFactory()))
		{
            if (isWindowFunction) {
                aggregatorClass.append(ClassName.WindowMaxMinAggregator);
            }
            else {
                aggregatorClass.append(ClassName.MaxMinAggregator);
            }
			
			return dts;
		}
		return null;
	}

	/**
	 * This is set by the parser.
	 */
	public final void setMaxOrMin(boolean isMax)
	{
		this.isMax = isMax;
	}

	/**
	 * Return if the aggregator class is for min/max.
	 *
	 * @return boolean true/false
	 */
	public final boolean isMax()
	{
		return(isMax);
	}

    public final boolean isWindowFunction() {
        return this.isWindowFunction;
    }

    public void setWindowFunction(boolean isWindowFunction) {
        this.isWindowFunction = isWindowFunction;
    }
}
