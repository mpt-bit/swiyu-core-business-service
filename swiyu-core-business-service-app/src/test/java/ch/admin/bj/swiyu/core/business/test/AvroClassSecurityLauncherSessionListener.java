package ch.admin.bj.swiyu.core.business.test;

import ch.admin.bit.jeap.messaging.avro.security.AvroClassSecurity;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/**
 * Installs the jeap Avro class whitelist once per test JVM session so that Avro message builders work in every test,
 * regardless of the test slice, execution order or runner (IntelliJ or Maven). Sliced tests (e.g. {@code @DataJpaTest})
 * do not load the jeap messaging auto-configuration that would otherwise install the whitelist, which previously caused
 * intermittent {@link SecurityException}s when such a test was run in isolation.
 */
public class AvroClassSecurityLauncherSessionListener implements LauncherSessionListener {

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        AvroClassSecurity.installDefaultIfMissing();
    }
}
