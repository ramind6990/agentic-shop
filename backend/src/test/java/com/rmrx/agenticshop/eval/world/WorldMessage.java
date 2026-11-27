package com.rmrx.agenticshop.eval.world;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rmrx.agenticshop.app.support.entity.AuthorType;

import java.time.Instant;

/** One message of a ticket. */
public class WorldMessage {

    public AuthorType author = AuthorType.CUSTOMER;
    public String body;
    public boolean internal;
    public String at;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Instant createdAt;
}
