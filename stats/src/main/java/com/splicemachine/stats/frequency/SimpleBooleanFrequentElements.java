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

package com.splicemachine.stats.frequency;

import org.sparkproject.guava.collect.Sets;

import java.util.Collections;
import java.util.Set;

/**
 * @author Scott Fines
 *         Date: 12/5/14
 */
class SimpleBooleanFrequentElements implements BooleanFrequentElements {
    private Freq trueValue;
    private Freq falseValue;

    public SimpleBooleanFrequentElements(long trueCount, long falseCount) {
        this.trueValue = new Freq();
        this.trueValue.count = trueCount;
        this.trueValue.value = true;

        this.falseValue = new Freq();
        this.falseValue.count = falseCount;
        this.falseValue.value = false;
    }

    @Override public BooleanFrequencyEstimate equalsTrue() { return trueValue; }
    @Override public BooleanFrequencyEstimate equalsFalse() { return falseValue; }
    @Override public BooleanFrequencyEstimate equals(boolean value) { return value? trueValue: falseValue; }

    @Override
    public BooleanFrequentElements getClone() {
        return new SimpleBooleanFrequentElements(trueValue.count,falseValue.count);
    }

    @Override
    public FrequencyEstimate<? extends Boolean> equal(Boolean item) {
        assert item!=null: "Cannot estimate frequency of null value!";
        return equals(item.booleanValue());
    }

    @Override
    public Set<? extends FrequencyEstimate<Boolean>> allFrequentElements() {
        return Sets.newHashSet(trueValue,falseValue);
    }

    @Override
    public long totalFrequentElements() {
        return trueValue.count+falseValue.count;
    }

    @Override
    public String toString(){
        return trueValue.toString()+","+falseValue.toString();
    }

    @Override
    public Set<? extends FrequencyEstimate<Boolean>> frequentElementsBetween(Boolean start, Boolean stop, boolean includeMin, boolean includeStop) {
        /*
         * we arbitrarily decided that true < false. Why, do you ask? Because we can, that's why. If you don't like it,
         * why do you care?
         */
        if(start==null){
            if(stop==null){
                //include everything
                return Sets.newHashSet(trueValue, falseValue);
            }else if(stop==Boolean.FALSE){
                if(includeStop)
                    return Sets.newHashSet(trueValue,falseValue);
                else return Collections.singleton(trueValue);
            }else {
                if(includeMin || includeStop) return Collections.singleton(trueValue);
                else return Collections.emptySet();
            }
        }else if(stop==null){
            if(start==Boolean.TRUE){
                if(includeMin)
                    return Sets.newHashSet(trueValue,falseValue);
                else return Collections.singleton(falseValue);
            }else{
                if(includeMin||includeStop) return Collections.singleton(falseValue);
                else return Collections.emptySet();
            }
        }else{
            if(start==stop){
                if(includeMin||includeStop) return start==Boolean.TRUE? Collections.singleton(trueValue): Collections.singleton(falseValue);
            }
            if(start==Boolean.TRUE){
                //stop equals false
                if(includeMin && includeStop) return Sets.newHashSet(trueValue,falseValue);
                else if(includeMin) return Collections.singleton(trueValue);
                else if(includeStop) return Collections.singleton(falseValue);
                else return Collections.emptySet();
            }else{
                if (includeMin) return Collections.singleton(trueValue);
                else return Collections.emptySet();
            }
        }
    }

    @Override
    public FrequentElements<Boolean> merge(FrequentElements<Boolean> other) {
        if(other instanceof BooleanFrequentElements){
            return merge((BooleanFrequentElements)other);
        }else {
            trueValue.count += other.equal(Boolean.TRUE).count();
            falseValue.count += other.equal(Boolean.FALSE).count();
            return this;
        }
    }

    //    @Override
    public BooleanFrequentElements merge(BooleanFrequentElements other) {
        trueValue.count+=other.equalsTrue().count();
        falseValue.count+=other.equalsFalse().count();
        return this;
    }

    private static class Freq implements BooleanFrequencyEstimate{
        boolean value;
        long count;

        @Override public boolean value() { return value; }
        @Override public Boolean getValue() { return value; }
        @Override public long count() { return count; }
        @Override public long error() { return 0; }

        @Override
        public FrequencyEstimate<Boolean> merge(FrequencyEstimate<Boolean> other) {
            this.count+=other.count();
            return this;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Freq freq = (Freq) o;

            return value == freq.value;
        }

        @Override public int hashCode() { return (value ? 1 : 0); }

        @Override
        public String toString(){
            return "("+value+","+count+")";
        }
    }
}
