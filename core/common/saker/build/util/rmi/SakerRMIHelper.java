package saker.build.util.rmi;

import saker.build.meta.PropertyNames;
import saker.build.thirdparty.saker.rmi.connection.RMIConnection;
import saker.build.thirdparty.saker.rmi.connection.RMIOptions;
import saker.build.thirdparty.saker.rmi.connection.RMIStatistics;
import testing.saker.build.flag.TestFlag;

public class SakerRMIHelper {
	private SakerRMIHelper() {
		throw new UnsupportedOperationException();
	}

	public static RMIOptions createBaseRMIOptions() {
		RMIOptions rmioptions = new RMIOptions();
		//collect statistics if the property is set
		boolean collectstats = System.getProperty(PropertyNames.PROPERTY_COLLECT_RMI_STATISTICS) != null;
		if (TestFlag.ENABLED) {
			collectstats = true;
		}
		rmioptions.collectStatistics(collectstats);
		return rmioptions;
	}

	public static void dumpRMIStatistics(RMIConnection connection) {
		RMIStatistics stats = connection.getStatistics();
		if (stats != null) {
			//lock to prevent interlacing
			StringBuilder sb = new StringBuilder();
			stats.dumpSummary(sb, null);
			System.err.println(sb);
		}
	}
}
