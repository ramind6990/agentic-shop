package com.rmrx.agenticshop.eval.world;

/**
 * A domain call that fails on purpose (chaos), e.g. {@code {call: ShipmentService.track, error: timeout, times: 2}}.
 *
 * @see Faults
 */
public class Fault {

    /** {@code <Service>.<method>}, as in the writes log. */
    public String call;
    /** {@code timeout}, {@code unavailable}, or the exception message to throw. */
    public String error = "unavailable";
    /** How many calls fail; 0 = every call. */
    public int times = 1;
}
