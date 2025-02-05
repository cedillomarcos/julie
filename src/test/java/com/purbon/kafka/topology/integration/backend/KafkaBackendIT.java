package com.purbon.kafka.topology.integration.backend;

import static com.purbon.kafka.topology.CommandLineInterface.BROKERS_OPTION;
import static com.purbon.kafka.topology.Constants.JULIE_INSTANCE_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.purbon.kafka.topology.Configuration;
import com.purbon.kafka.topology.Constants;
import com.purbon.kafka.topology.api.adminclient.TopologyBuilderAdminClient;
import com.purbon.kafka.topology.backend.BackendState;
import com.purbon.kafka.topology.backend.KafkaBackend;
import com.purbon.kafka.topology.integration.containerutils.ContainerFactory;
import com.purbon.kafka.topology.integration.containerutils.ContainerTestUtils;
import com.purbon.kafka.topology.integration.containerutils.SaslPlaintextKafkaContainer;
import com.purbon.kafka.topology.roles.TopologyAclBinding;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.resource.ResourceType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class KafkaBackendIT {

  Configuration config;
  Properties props;

  private static SaslPlaintextKafkaContainer container;

  @Before
  public void before() throws IOException {

    container = ContainerFactory.fetchSaslKafkaContainer(System.getProperty("cp.version"));
    container.start();

    props = new Properties();
    props.put(JULIE_INSTANCE_ID, "1234");
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "group.id");

    Map<String, Object> saslConfig =
        ContainerTestUtils.getSaslConfig(
            container.getBootstrapServers(),
            SaslPlaintextKafkaContainer.DEFAULT_SUPER_USERNAME,
            SaslPlaintextKafkaContainer.DEFAULT_SUPER_PASSWORD);
    saslConfig.forEach((k, v) -> props.setProperty(k, String.valueOf(v)));

    HashMap<String, String> cliOps = new HashMap<>();
    cliOps.put(BROKERS_OPTION, container.getBootstrapServers());

    config = new Configuration(cliOps, props);

    var adminClient = ContainerTestUtils.getSaslAdminClient(container);
    var topologyAdminClient = new TopologyBuilderAdminClient(adminClient);
    topologyAdminClient.createTopic(config.getJulieKafkaConfigTopic());
  }

  @After
  public void after() {
    container.stop();
  }

  @Test
  public void testExpectedFlow() throws IOException, InterruptedException {

    TopologyAclBinding binding =
        TopologyAclBinding.build(
            ResourceType.TOPIC.name(), "foo", "*", "Write", "User:foo", "LITERAL");

    BackendState state = new BackendState();
    state.addBindings(Collections.singleton(binding));

    KafkaBackend backend = new KafkaBackend();
    backend.configure(config);

    backend.save(state);
    backend.close();

    KafkaBackend newBackend = new KafkaBackend();
    newBackend.configure(config);

    Thread.sleep(3000);

    BackendState newState = newBackend.load();
    assertThat(newState.size()).isEqualTo(1);
    assertThat(newState.getBindings()).contains(binding);
    newBackend.close();
  }

  @Test(expected = IOException.class)
  public void testWrongConfig() {

    KafkaBackend backend = new KafkaBackend();

    HashMap<String, String> cliOps = new HashMap<>();
    cliOps.put(BROKERS_OPTION, container.getBootstrapServers());

    props.put(Constants.JULIE_KAFKA_CONFIG_TOPIC, "foo");

    Configuration config = new Configuration(cliOps, props);

    backend.configure(config);
    backend.close();
  }
}
