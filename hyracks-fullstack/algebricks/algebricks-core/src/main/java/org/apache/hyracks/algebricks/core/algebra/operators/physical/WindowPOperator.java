/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.hyracks.algebricks.core.algebra.operators.physical;

import java.util.List;

import org.apache.hyracks.algebricks.core.algebra.base.LogicalVariable;
import org.apache.hyracks.algebricks.core.algebra.base.PhysicalOperatorTag;
import org.apache.hyracks.algebricks.core.algebra.operators.logical.WindowOperator;
import org.apache.hyracks.algebricks.core.algebra.properties.OrderColumn;
import org.apache.hyracks.algebricks.core.jobgen.impl.JobGenContext;
import org.apache.hyracks.algebricks.runtime.base.IRunningAggregateEvaluatorFactory;
import org.apache.hyracks.algebricks.runtime.base.IScalarEvaluatorFactory;
import org.apache.hyracks.algebricks.runtime.operators.win.AbstractWindowRuntimeFactory;
import org.apache.hyracks.algebricks.runtime.operators.win.WindowAggregatorDescriptorFactory;
import org.apache.hyracks.algebricks.runtime.operators.win.WindowMaterializingRuntimeFactory;
import org.apache.hyracks.algebricks.runtime.operators.win.WindowNestedPlansRunningRuntimeFactory;
import org.apache.hyracks.algebricks.runtime.operators.win.WindowNestedPlansRuntimeFactory;
import org.apache.hyracks.algebricks.runtime.operators.win.WindowNestedPlansUnboundedRuntimeFactory;
import org.apache.hyracks.api.dataflow.value.IBinaryComparatorFactory;

public final class WindowPOperator extends AbstractWindowPOperator {

    private final boolean frameStartIsMonotonic;

    private final boolean frameEndIsMonotonic;

    private final boolean nestedTrivialAggregates;

    // The maximum number of in-memory frames that this operator can use.
    private final int memSizeInFrames;

    public WindowPOperator(List<LogicalVariable> partitionColumns, List<OrderColumn> orderColumns,
            boolean frameStartIsMonotonic, boolean frameEndIsMonotonic, boolean nestedTrivialAggregates,
            int memSizeInFrames) {
        super(partitionColumns, orderColumns);
        this.frameStartIsMonotonic = frameStartIsMonotonic;
        this.frameEndIsMonotonic = frameEndIsMonotonic;
        this.nestedTrivialAggregates = nestedTrivialAggregates;
        this.memSizeInFrames = memSizeInFrames;
    }

    @Override
    public PhysicalOperatorTag getOperatorTag() {
        return PhysicalOperatorTag.WINDOW;
    }

    @Override
    protected AbstractWindowRuntimeFactory createRuntimeFactory(WindowOperator winOp, int[] partitionColumnsList,
            IBinaryComparatorFactory[] partitionComparatorFactories,
            IBinaryComparatorFactory[] orderComparatorFactories, IScalarEvaluatorFactory[] frameValueExprEvals,
            IBinaryComparatorFactory[] frameValueComparatorFactories, IScalarEvaluatorFactory[] frameStartExprEvals,
            IScalarEvaluatorFactory[] frameStartValidationExprEvals, IScalarEvaluatorFactory[] frameEndExprEvals,
            IScalarEvaluatorFactory[] frameEndValidationExprEvals, IScalarEvaluatorFactory[] frameExcludeExprEvals,
            IBinaryComparatorFactory[] frameExcludeComparatorFactories, IScalarEvaluatorFactory frameOffsetExprEval,
            int[] projectionColumnsExcludingSubplans, int[] runningAggOutColumns,
            IRunningAggregateEvaluatorFactory[] runningAggFactories, int nestedAggOutSchemaSize,
            WindowAggregatorDescriptorFactory nestedAggFactory, JobGenContext context) {

        // special cases
        if (!winOp.hasNestedPlans()) {
            return new WindowMaterializingRuntimeFactory(partitionColumnsList, partitionComparatorFactories,
                    orderComparatorFactories, projectionColumnsExcludingSubplans, runningAggOutColumns,
                    runningAggFactories, memSizeInFrames);
        }

        boolean hasFrameStart = frameStartExprEvals != null && frameStartExprEvals.length > 0;
        boolean hasFrameEnd = frameEndExprEvals != null && frameEndExprEvals.length > 0;
        boolean hasFrameExclude = frameExcludeExprEvals != null && frameExcludeExprEvals.length > 0;
        boolean hasFrameOffset = frameOffsetExprEval != null;
        if (!hasFrameStart && !hasFrameExclude && !hasFrameOffset) {
            if (!hasFrameEnd) {
                // special case #1: frame == whole partition, no exclusions, no offset
                return new WindowNestedPlansUnboundedRuntimeFactory(partitionColumnsList, partitionComparatorFactories,
                        orderComparatorFactories, winOp.getFrameMaxObjects(), projectionColumnsExcludingSubplans,
                        runningAggOutColumns, runningAggFactories, nestedAggOutSchemaSize, nestedAggFactory,
                        memSizeInFrames);
            } else if (frameEndIsMonotonic && nestedTrivialAggregates) {
                // special case #2: accumulating frame from beginning of the partition, no exclusions, no offset,
                //                  trivial aggregate subplan ( aggregate + nts )
                nestedAggFactory.setPartialOutputEnabled(true);
                return new WindowNestedPlansRunningRuntimeFactory(partitionColumnsList, partitionComparatorFactories,
                        orderComparatorFactories, frameValueExprEvals, frameValueComparatorFactories, frameEndExprEvals,
                        frameEndValidationExprEvals, winOp.getFrameMaxObjects(),
                        context.getBinaryBooleanInspectorFactory(), projectionColumnsExcludingSubplans,
                        runningAggOutColumns, runningAggFactories, nestedAggOutSchemaSize, nestedAggFactory,
                        memSizeInFrames);
            }
        }

        // default case
        return new WindowNestedPlansRuntimeFactory(partitionColumnsList, partitionComparatorFactories,
                orderComparatorFactories, frameValueExprEvals, frameValueComparatorFactories, frameStartExprEvals,
                frameStartValidationExprEvals, frameStartIsMonotonic, frameEndExprEvals, frameEndValidationExprEvals,
                frameExcludeExprEvals, winOp.getFrameExcludeNegationStartIdx(), frameExcludeComparatorFactories,
                frameOffsetExprEval, winOp.getFrameMaxObjects(), context.getBinaryBooleanInspectorFactory(),
                context.getBinaryIntegerInspectorFactory(), projectionColumnsExcludingSubplans, runningAggOutColumns,
                runningAggFactories, nestedAggOutSchemaSize, nestedAggFactory, memSizeInFrames);
    }
}
