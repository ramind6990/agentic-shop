package com.rmrx.agenticshop.eval.world;

import com.fasterxml.jackson.annotation.JsonProperty;

/** A saved address of a customer. */
public class WorldAddress {

    public Long id;
    /** Username of the customer; default: the actor. */
    public String owner;
    public String label = "Home";
    public String recipient;
    public String line1 = "12 Main Street";
    public String line2;
    public String city = "Springfield";
    public String region = "IL";
    public String postalCode = "62701";
    public String country = "US";
    @JsonProperty("default")
    public boolean isDefault;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Long ownerId;
}
