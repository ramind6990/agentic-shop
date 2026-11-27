package com.rmrx.agenticshop.eval.scenario;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every scenario file can be read (no Spring, no database, no model): catches YAML typos with plain {@code mvn test}
 * before an eval run spends money.
 */
class ScenarioFilesTest {

    @TestFactory
    Stream<DynamicTest> scenarioFilesAreValid() {
        return ScenarioLoader.ids("**").stream().map(id -> DynamicTest.dynamicTest(id, () -> {
            Scenario scenario = ScenarioLoader.load(id);
            assertThat(scenario.expect()).as("expect section").isNotNull();
            assertThat(scenario.world().orders).allSatisfy(o -> assertThat(o.number).isNotBlank());
        }));
    }

    @TestFactory
    Stream<DynamicTest> globsSelectScenarios() {
        return Stream.of(
                DynamicTest.dynamicTest("orders/*", () -> assertThat(ScenarioLoader.ids("orders/*")).isNotEmpty()),
                DynamicTest.dynamicTest("no match", () -> assertThat(ScenarioLoader.ids("nothing/*")).isEmpty()),
                DynamicTest.dynamicTest("list", () -> assertThat(ScenarioLoader.ids("orders/cancel-*,orders/track-*"))
                        .allMatch(id -> id.startsWith("orders/cancel-") || id.startsWith("orders/track-"))));
    }
}
