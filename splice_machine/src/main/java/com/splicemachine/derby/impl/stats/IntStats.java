package com.splicemachine.derby.impl.stats;

import com.google.common.base.Function;
import com.splicemachine.stats.ColumnStatistics;
import com.splicemachine.stats.IntColumnStatistics;
import com.splicemachine.stats.frequency.FrequencyEstimate;
import com.splicemachine.stats.frequency.FrequentElements;
import com.splicemachine.stats.frequency.IntFrequencyEstimate;
import com.splicemachine.stats.frequency.IntFrequentElements;
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.types.DataValueDescriptor;
import org.apache.derby.iapi.types.SQLInteger;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Set;

/**
 * @author Scott Fines
 *         Date: 2/27/15
 */
public class IntStats extends BaseDvdStatistics {
    private IntColumnStatistics intStats; //integer-typed reference
    public IntStats() { }

    public IntStats(IntColumnStatistics build) {
        super(build);
        this.intStats = build;
    }

    @Override
    public FrequentElements<DataValueDescriptor> topK() {
        return new IntFreqs((IntFrequentElements)intStats.topK());
    }

    @Override public DataValueDescriptor minValue() { return new SQLInteger(intStats.min()); }
    @Override public DataValueDescriptor maxValue() { return new SQLInteger(intStats.max()); }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        IntColumnStatistics.encoder().encode(intStats,out);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        baseStats = intStats = IntColumnStatistics.encoder().decode(in);
    }

    @Override
    public ColumnStatistics<DataValueDescriptor> getClone() {
        return new IntStats((IntColumnStatistics)intStats.getClone());
    }

    /* ****************************************************************************************************************/
    /*private helper methods*/
    private class IntFreqs implements FrequentElements<DataValueDescriptor> {
        private IntFrequentElements frequentElements;

        public IntFreqs(IntFrequentElements freqs) {
            this.frequentElements = freqs;
        }

        @Override
        public FrequentElements<DataValueDescriptor> getClone() {
            return new IntFreqs((IntFrequentElements)frequentElements.getClone());
        }

        @Override
        @SuppressWarnings("unchecked")
        public Set<? extends FrequencyEstimate<DataValueDescriptor>> allFrequentElements() {
            return convert((Set<IntFrequencyEstimate>)frequentElements.allFrequentElements());
        }

        @Override
        public FrequencyEstimate<? extends DataValueDescriptor> equal(DataValueDescriptor element) {
            try {
                return new IntFreq(frequentElements.countEqual(element.getInt()));
            } catch (StandardException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Set<? extends FrequencyEstimate<DataValueDescriptor>> frequentElementsBetween(
                DataValueDescriptor start, DataValueDescriptor stop, boolean includeStart, boolean includeStop) {
            try {
                if (start == null || start.isNull()) {
                    if (stop == null || stop.isNull()) {
                        //get everything
                        return allFrequentElements();
                    }
                    else return convert(frequentElements.frequentBefore(stop.getInt(), includeStop));
                }else if(stop==null || stop.isNull())
                    return convert(frequentElements.frequentAfter(start.getInt(),includeStart));
                else
                    return convert(frequentElements.frequentBetween(start.getInt(),stop.getInt(),includeStart,includeStop));
            }catch(StandardException se){
                throw new RuntimeException(se); //shouldn't happen
            }
        }

        @Override
        public FrequentElements<DataValueDescriptor> merge(FrequentElements<DataValueDescriptor> other) {
            assert other instanceof IntFreqs : "Cannot merge FrequentElements of type " + other.getClass();
            frequentElements = frequentElements.merge(((IntFreqs) other).frequentElements);
            return this;
        }

        private Set<? extends FrequencyEstimate<DataValueDescriptor>> convert(Set<IntFrequencyEstimate> other) {
            return new ConvertingSetView<>(other,conversionFunction);
        }
    }

    private static class IntFreq implements FrequencyEstimate<DataValueDescriptor> {
        private IntFrequencyEstimate baseEstimate;

        public IntFreq(IntFrequencyEstimate intFrequencyEstimate) {
            this.baseEstimate = intFrequencyEstimate;
        }

        @Override public DataValueDescriptor getValue() { return new SQLInteger(baseEstimate.value()); }
        @Override public long count() { return baseEstimate.count(); }
        @Override public long error() { return baseEstimate.error(); }

        @Override
        public FrequencyEstimate<DataValueDescriptor> merge(FrequencyEstimate<DataValueDescriptor> other) {
            assert other instanceof IntFreq: "Cannot merge FrequencyEstimate of type "+ other.getClass();
            baseEstimate = (IntFrequencyEstimate)baseEstimate.merge(((IntFreq) other).baseEstimate);
            return this;
        }

        @Override public String toString() { return baseEstimate.toString(); }
    }

    private static final Function<IntFrequencyEstimate,FrequencyEstimate<DataValueDescriptor>> conversionFunction
                                                = new Function<IntFrequencyEstimate, FrequencyEstimate<DataValueDescriptor>>() {
        @Override
        public FrequencyEstimate<DataValueDescriptor> apply(IntFrequencyEstimate intFrequencyEstimate) {
            return new IntFreq(intFrequencyEstimate);
        }
    };

}