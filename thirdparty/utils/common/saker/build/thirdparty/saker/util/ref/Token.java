package saker.build.thirdparty.saker.util.ref;

/**
 * Common superinterface for tokens which play a role in keeping weakly referenced objects from garbage collection.
 * <p>
 * These are mainly used when some objects are added to a consumer and the consumer will reference the object weakly. In
 * this case the consumer returns a token which should be kept until the client is done with the consumer.
 * <p>
 * When the token is no longer referenced, the added object to the consumer might be freely garbage collected, and
 * therefore automatically uninstalling the object from the consumer.
 * <p>
 * This is mainly useful when event listeners are installed over an RMI connection. It can cause a memory leak if the
 * consumer strongly references the listener, as abruptly terminating the RMI connection will leave the listener
 * installed. By weakly referencing, and returning a token, when the token is no longer referenced, the consumer can
 * automatically uninstall the listener when it is garbage collected.
 * <p>
 * <b>Note</b> that in most cases explicitly uninstalling the object from the consumer is beneficial rather than
 * <code>null</code>ing out the references to the token.
 * <p>
 * Tokens should strongly reference the subject of the operation.
 * 
 * @see WeakReferencedToken
 */
public interface Token {
}
