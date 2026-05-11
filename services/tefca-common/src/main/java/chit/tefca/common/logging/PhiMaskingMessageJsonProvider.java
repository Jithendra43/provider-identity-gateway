package chit.tefca.common.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import net.logstash.logback.composite.loggingevent.MessageJsonProvider;

import java.io.IOException;

/**
 * Logstash JSON provider that masks PHI in the {@code message} field before
 * it is serialised. Wired in {@code logback-spring.xml}:
 * <pre>
 *   &lt;encoder class="net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder"&gt;
 *     &lt;providers&gt;
 *       &lt;provider class="chit.tefca.common.logging.PhiMaskingMessageJsonProvider"/&gt;
 *       ...
 *     &lt;/providers&gt;
 *   &lt;/encoder&gt;
 * </pre>
 */
public class PhiMaskingMessageJsonProvider extends MessageJsonProvider {

    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {
        String masked = PhiMaskingConverter.mask(event.getFormattedMessage());
        generator.writeStringField(getFieldName() == null ? "message" : getFieldName(), masked);
    }
}
