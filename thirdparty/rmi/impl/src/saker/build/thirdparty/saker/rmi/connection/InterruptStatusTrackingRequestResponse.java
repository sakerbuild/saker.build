package saker.build.thirdparty.saker.rmi.connection;

class InterruptStatusTrackingRequestResponse implements RequestResponse {
	private final boolean invokerThreadInterrupted;
	private final int deliveredInterruptRequestCount;

	public InterruptStatusTrackingRequestResponse(boolean invokerThreadInterrupted,
			int deliveredInterruptRequestCount) {
		this.invokerThreadInterrupted = invokerThreadInterrupted;
		this.deliveredInterruptRequestCount = deliveredInterruptRequestCount;
	}

	public final boolean isInvokerThreadInterrupted() {
		return invokerThreadInterrupted;
	}

	public final int getDeliveredInterruptRequestCount() {
		return deliveredInterruptRequestCount;
	}
}
