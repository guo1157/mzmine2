/*
 * Copyright 2006-2009 The MZmine 2 Development Team
 * 
 * This file is part of MZmine 2.
 * 
 * MZmine 2 is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * MZmine 2 is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * MZmine 2; if not, write to the Free Software Foundation, Inc., 51 Franklin St,
 * Fifth Floor, Boston, MA 02110-1301 USA
 */

package net.sf.mzmine.modules.peakpicking.msms;

import java.util.logging.Logger;

import net.sf.mzmine.data.DataPoint;
import net.sf.mzmine.data.PeakList;
import net.sf.mzmine.data.PeakListRow;
import net.sf.mzmine.data.PeakStatus;
import net.sf.mzmine.data.RawDataFile;
import net.sf.mzmine.data.Scan;
import net.sf.mzmine.data.impl.SimpleChromatographicPeak;
import net.sf.mzmine.data.impl.SimplePeakList;
import net.sf.mzmine.data.impl.SimplePeakListRow;
import net.sf.mzmine.main.mzmineclient.MZmineCore;
import net.sf.mzmine.project.MZmineProject;
import net.sf.mzmine.taskcontrol.Task;
import net.sf.mzmine.util.Range;
import net.sf.mzmine.util.ScanUtils;

public class MsMsPeakPickingTask implements Task {
	private Logger logger = Logger.getLogger(this.getClass().getName());

	private TaskStatus status = TaskStatus.WAITING;
	private String errorMessage;

	private int processedScans, totalScans;

	private RawDataFile dataFile;
	private PeakList peakList;
	private double binSize;

	public MsMsPeakPickingTask(RawDataFile dataFile, MsMsPeakPickerParameters parameters) {
		this.dataFile = dataFile;
		binSize = (Double) parameters
				.getParameterValue(MsMsPeakPickerParameters.mzRange);
		this.peakList = new SimplePeakList(dataFile.getName(), dataFile);

	}

	public RawDataFile getDataFile() {
		return dataFile;
	}

	public void cancel() {
		status = TaskStatus.CANCELED;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public double getFinishedPercentage() {
		if (totalScans == 0)
			return 0f;
		return (double) processedScans / totalScans;
	}

	public TaskStatus getStatus() {
		return status;
	}

	public String getTaskDescription() {
		return "Building MS/MS Peaklist based on MS/MS from " + dataFile;
	}

	public void run() {
		status = TaskStatus.PROCESSING;

		logger.finest("Start building ...");
		int[] scanNumbers = dataFile.getScanNumbers(2);
		totalScans = scanNumbers.length;
		for (int scanNumber : scanNumbers) {
			if (status == TaskStatus.CANCELED)
				return;

			// Get next MS/MS scan
			Scan scan = dataFile.getScan(scanNumber);

			// no parents scan for this msms scan
			if (scan.getParentScanNumber() <= 0) {
				continue;
			}

			// Get the MS Scan
			Scan parentScan = dataFile.getScan(scan.getParentScanNumber());
			DataPoint p = ScanUtils.findBasePeak(parentScan, new Range(scan
					.getPrecursorMZ()
					- (binSize / 2.0f), scan.getPrecursorMZ()
					+ (binSize / 2.0f)));
			// no datapoint found
			if (p == null) {
				continue;
			}
			SimpleChromatographicPeak c = new SimpleChromatographicPeak(
					dataFile, scan.getPrecursorMZ(), parentScan
							.getRetentionTime(), p.getIntensity(), p
							.getIntensity(), new int[] { parentScan
							.getScanNumber() }, new DataPoint[] { p },
					PeakStatus.DETECTED, parentScan.getScanNumber(), scan
							.getScanNumber(), new Range(parentScan
							.getRetentionTime(), scan.getRetentionTime()),
					new Range(scan.getPrecursorMZ()), new Range(p
							.getIntensity()));

			PeakListRow entry = new SimplePeakListRow(scan.getScanNumber());
			entry.addPeak(dataFile, c);

			peakList.addRow(entry);
			processedScans++;
		}

		MZmineProject currentProject = MZmineCore.getCurrentProject();
		currentProject.addPeakList(peakList);
		logger.finest("Finished peaklist builder based on MS/MS"
				+ processedScans + " scans processed");

		status = TaskStatus.FINISHED;
	}

}