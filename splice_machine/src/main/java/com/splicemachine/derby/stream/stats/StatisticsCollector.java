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

package com.splicemachine.derby.stream.stats;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.splicemachine.EngineDriver;
import com.splicemachine.access.api.SConfiguration;
import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.sql.execute.ExecRow;
import com.splicemachine.db.iapi.types.DataValueDescriptor;
import com.splicemachine.derby.impl.sql.execute.operations.scanner.SITableScanner;
import com.splicemachine.derby.impl.stats.DvdStatsCollector;
import com.splicemachine.derby.impl.stats.SimpleOverheadManagedPartitionStatistics;
import com.splicemachine.metrics.Metrics;
import com.splicemachine.metrics.Timer;
import com.splicemachine.si.api.txn.TxnView;
import com.splicemachine.si.constants.SIConstants;
import com.splicemachine.si.impl.driver.SIDriver;
import com.splicemachine.stats.ColumnStatistics;
import com.splicemachine.stats.collector.ColumnStatsCollector;
import com.splicemachine.storage.DataResult;
import com.splicemachine.storage.DataResultScanner;
import com.splicemachine.storage.DataScan;
import com.splicemachine.storage.DataScanner;
import com.splicemachine.storage.Partition;


public class StatisticsCollector {
    protected final TxnView txn;
    private final ExecRow template;
    /*
     * A reverse mapping between the output column position and that column's position in
     * the original row.
     */
    private final int[] columnPositionMap;
    /*
     * The maximum length of any individual fields. -1 if there is no maximum length.
     *
     * This is primarily useful for string fields. Most other types will have no maximum length
     */
    private final int[] lengths;
    private final long tableConglomerateId;
    private final SITableScanner scanner;
    private final String regionId;

    protected transient long openScannerTimeMicros = -1l;
    protected transient long closeScannerTimeMicros = -1l;
    private ColumnStatsCollector<DataValueDescriptor>[] dvdCollectors;
    private int[] fieldLengths;

    @SuppressFBWarnings(value="EI_EXPOSE_REP2", justification="Intentional")
    public StatisticsCollector(TxnView txn,
                               ExecRow template,
                               int[] columnPositionMap,
                               int[] lengths,
                               SITableScanner scanner) {
        this.txn = txn;
        this.template = template;
        this.columnPositionMap = columnPositionMap;
        this.lengths = lengths;
        this.scanner = scanner;
        DataScanner regionScanner = scanner.getRegionScanner();
        Partition region = regionScanner.getPartition();
        String conglomId = region.getTableName();
        regionId = region.getName();
        tableConglomerateId = Long.parseLong(conglomId);
        dvdCollectors = getCollectors();
        fieldLengths = new int[dvdCollectors.length];
    }

    @SuppressWarnings("unchecked")
    public void collect(ExecRow row) throws ExecutionException {
        try{
            updateRow(scanner, dvdCollectors, fieldLengths, row);
        } catch (StandardException | IOException e) {
            throw new ExecutionException(e); //should only be IOExceptions
        }
    }

    public SimpleOverheadManagedPartitionStatistics getStatistics() throws ExecutionException {
        List<ColumnStatistics> columnStats = getFinalColumnStats(dvdCollectors);

//        TimeView readTime = scanner.getTime();
        long byteCount = scanner.getBytesOutput();
        long rowCount = scanner.getRowsVisited() - scanner.getRowsFiltered();
//        long localReadTimeMicros = readTime.getWallClockTime() / 1000; //scale to microseconds
//        long remoteReadTimeMicros = getRemoteReadTime(rowCount);
//        if (remoteReadTimeMicros > 0) {
//            remoteReadTimeMicros /= 1000;
//        }
        return SimpleOverheadManagedPartitionStatistics.create(
                Long.toString(tableConglomerateId),
                regionId,
                rowCount,
                byteCount,
                columnStats);

    }
    protected long getCloseScannerEvents(){ return 1l; }

    protected long getOpenScannerTimeMicros() throws ExecutionException{ return openScannerTimeMicros; }
    protected long  getCloseScannerTimeMicros(){ return closeScannerTimeMicros; }

    protected long getOpenScannerEvents(){ return 1l; }

    protected void closeResources() {

    }

    protected void updateRow(SITableScanner scanner,
                             ColumnStatsCollector<DataValueDescriptor>[] dvdCollectors,
                             int[] fieldLengths,
                             ExecRow row) throws StandardException, IOException {
        scanner.recordFieldLengths(fieldLengths); //get the size of each column
        DataValueDescriptor[] dvds = row.getRowArray();
        for (int i = 0; i < dvds.length; i++) {
            DataValueDescriptor dvd = dvds[i];
            dvdCollectors[i].update(dvd);
            dvdCollectors[i].updateSize(fieldLengths[i]);
        }
    }

    protected long getRemoteReadTime(long rowCount) throws ExecutionException{
        /*
         * for base table scans, we want to do something akin to a self-join here. Really what we
         * need to do is read a sample of rows remotely, and measure their overall latency. That way,
         * we will know (roughly) the cost of reading this table.
         *
         * However, we don't want to measure the latency of reading from this region, since it would
         * be much more efficient than we normally would want. Instead, we try and pick regions
         * which do not contain the partitionScan. Of course, if the region contains everything,
         * then who cares.
         *
         * Once a region is selected, we read in some rows using a scanner, measure the average latency,
         * and then use that to estimate how long it would take to read us remotely
         */
        //TODO -sf- randomize this a bit so we don't hotspot a region
        int fetchSampleSize=EngineDriver.driver().getConfiguration().getIndexFetchSampleSize();
        Timer remoteReadTimer = Metrics.newWallTimer();
        DataScan scan = SIDriver.driver().getOperationFactory().newDataScan(txn);
        scan.startKey(SIConstants.EMPTY_BYTE_ARRAY).stopKey(SIConstants.EMPTY_BYTE_ARRAY);
        int n =2;
        long totalOpenTime = 0;
        long totalCloseTime = 0;
        int iterations = 0;
        try(Partition table = SIDriver.driver().getTableFactory().getTable(Long.toString(tableConglomerateId))){
            while(n<fetchSampleSize){
                scan.batchCells(n).cacheRows(n);
                long openTime=System.nanoTime();
                long closeTime;
                try(DataResultScanner scanner=table.openResultScanner(scan)){
                    totalOpenTime+=(System.nanoTime()-openTime);
                    int pos=0;
                    remoteReadTimer.startTiming();
                    DataResult result;
                    while(pos<n && (result=scanner.next())!=null){
                        pos++;
                    }
                    remoteReadTimer.tick(pos);
                    closeTime=System.nanoTime();
                }
                totalCloseTime+=(System.nanoTime()-closeTime);
                iterations++;
                n<<=1;
            }

        }catch(IOException e){
            throw new ExecutionException(e);
        }
        //stash the scanner open and scanner close times
        openScannerTimeMicros=totalOpenTime/(1000*iterations);
        closeScannerTimeMicros=totalCloseTime/(1000*iterations);
        double latency=((double)remoteReadTimer.getTime().getWallClockTime())/remoteReadTimer.getNumEvents();
        return Math.round(latency*rowCount);
    }

    protected List<ColumnStatistics> getFinalColumnStats(ColumnStatsCollector<DataValueDescriptor>[] dvdCollectors) {
        List<ColumnStatistics> columnStats = new ArrayList<>(dvdCollectors.length);
        for (int i = 0; i < dvdCollectors.length; i++) {
            columnStats.add(dvdCollectors[i].build());
        }
        return columnStats;
    }

    protected void populateCollectors(DataValueDescriptor[] dvds,
                                      ColumnStatsCollector<DataValueDescriptor>[] collectors) {
        SConfiguration configuration=EngineDriver.driver().getConfiguration();
        int cardinalityPrecision = configuration.getCardinalityPrecision();
        int topKSize = configuration.getTopkSize();
        for(int i=0;i<dvds.length;i++){
            DataValueDescriptor dvd = dvds[i];
            int columnId = columnPositionMap[i];
            int columnLength = lengths[i];
            collectors[i] = DvdStatsCollector.newCollector(columnId, dvd.getTypeFormatId(), columnLength, topKSize, cardinalityPrecision);
        }
    }

    /* ****************************************************************************************************************/
    /*private helper methods*/

    @SuppressWarnings("unchecked")
    private ColumnStatsCollector<DataValueDescriptor>[] getCollectors() {
        DataValueDescriptor[] dvds = template.getRowArray();
        ColumnStatsCollector<DataValueDescriptor> [] collectors = new ColumnStatsCollector[dvds.length];
        populateCollectors(dvds, collectors);
        return collectors;
    }
}
