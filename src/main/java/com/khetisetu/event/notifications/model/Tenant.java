package com.khetisetu.event.notifications.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A product this service sends on behalf of — khetisetu, dyopact, and whatever
 * comes next. One document per product.
 *
 * <p>Exists so that identity lives HERE rather than in the message. Producers
 * previously read an {@link EmailSenderConfig} out of Mongo and embedded the
 * whole thing — SMTP host, username and password included — into every Kafka
 * payload. Those credentials were never actually used to send anything (mail
 * goes out through Brevo/SES with their own credentials), so they were pure
 * leak: readable by anyone with topic access and persisted on the broker's disk
 * under retention. A producer now sends a tenantId and nothing else.
 *
 * <p>The id is the tenant key used on the wire ("khetisetu"), so it is set
 * explicitly rather than generated.
 */
@Data
@Document(collection = "tenants")
public class Tenant {

    /** Wire identifier, e.g. "khetisetu". Set explicitly, never generated. */
    @Id
    private String id;

    /** Human-readable product name, e.g. "KhetiSetu". */
    private String name;

    /** Disabled tenants are rejected rather than silently falling back to another product's identity. */
    private boolean enabled = true;

    /** From-address for outbound email, e.g. "support@khetisetu.com". */
    private String senderEmail;

    /** From-name for outbound email, e.g. "KhetiSetu". */
    private String senderName;

    /** Optional Reply-To; falls back to senderEmail when unset. */
    private String replyTo;

    /** Product's public site, used in template footers and links. */
    private String siteUrl;

    public String resolvedReplyTo() {
        return (replyTo == null || replyTo.isBlank()) ? senderEmail : replyTo;
    }
}
