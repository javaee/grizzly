import java.net.MalformedURLException;
import java.net.URL;

import org.junit.Test;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import static org.ops4j.pax.exam.CoreOptions.*;
import org.ops4j.pax.exam.Option;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.*;
import org.ops4j.pax.exam.junit.AppliesTo;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;

/** Provisioning integration tests. Based on PAX Exam sample IT. */
@RunWith(JUnit4TestRunner.class)
public class ContextAnnotationTest {

    /**
     * Pax Exam test options that provisions the Pax URL mvn: url handler
     * bundle.
     *
     * @return integration tests options
     */
    @Configuration @AppliesTo("validURL")
    public static Option[] configureForValidURL() {
        return options(
          provision(
            "mvn:org.ops4j.pax.url/pax-url-mvn"), logProfile());
    }

    /**
     * Tests that the Pax URL mvn: url handler has been provisioned by creating
     * a mvn: url. If the url creation fails, it means that the bundle was not
     * provisioned.
     *
     * @throws MalformedURLException - Not expected
     */
    @Test @Ignore(
      value = "Stubing Integration tests, should never be part of normal build.")
    public void validURL() throws MalformedURLException {
        new URL("mvn:org.ops4j.pax.swissbox/pax-swissbox-core");
    }

    /**
     * Pax Exam test options that provisions the Pax URL mvn: url handler bundle
     * via a maven url option.
     *
     * @return integration tests options
     */
    @Configuration @AppliesTo("validMavenURL")
    public static Option[] configureForValidMavenURL() {
        return options(
          provision(
            mavenBundle().groupId("org.ops4j.pax.url")
              .artifactId("pax-url-mvn").version("0.3.2")), logProfile());
    }

    /**
     * Tests that the Pax URL mvn: url handler has been provisioned by creating
     * a mvn: url. If the url creation fails, it means that the bundle was not
     * provisioned.
     *
     * @throws MalformedURLException - Not expected
     */
    @Test @Ignore(
      value = "Stubing Integration tests, should never be part of normal build.")
    public void validMavenURL() throws MalformedURLException {
        new URL("mvn:org.ops4j.pax.swissbox/pax-swissbox-core");
    }

}