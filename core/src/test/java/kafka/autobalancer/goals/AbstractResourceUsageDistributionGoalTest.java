/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kafka.autobalancer.goals;

import kafka.autobalancer.common.Action;
import kafka.autobalancer.common.Resource;
import kafka.autobalancer.config.AutoBalancerControllerConfig;
import kafka.autobalancer.model.BrokerUpdater.Broker;
import kafka.autobalancer.model.ClusterModelSnapshot;
import kafka.autobalancer.model.TopicPartitionReplicaUpdater.TopicPartitionReplica;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

@Tag("S3Unit")
public class AbstractResourceUsageDistributionGoalTest extends GoalTestBase {
    private final Map<String, Goal> goalMap = new HashMap<>();

    @BeforeEach
    public void setup() {
        Map<String, Object> config = new HashMap<>();
        config.put(AutoBalancerControllerConfig.AUTO_BALANCER_CONTROLLER_GOALS, new StringJoiner(",")
                .add(NetworkInUsageDistributionGoal.class.getName())
                .add(NetworkOutUsageDistributionGoal.class.getName()).toString());
        config.put(AutoBalancerControllerConfig.AUTO_BALANCER_CONTROLLER_NETWORK_IN_USAGE_DISTRIBUTION_DETECT_THRESHOLD, 0);
        config.put(AutoBalancerControllerConfig.AUTO_BALANCER_CONTROLLER_NETWORK_OUT_USAGE_DISTRIBUTION_DETECT_THRESHOLD, 0);
        config.put(AutoBalancerControllerConfig.AUTO_BALANCER_CONTROLLER_NETWORK_IN_DISTRIBUTION_DETECT_AVG_DEVIATION, 0.2);
        config.put(AutoBalancerControllerConfig.AUTO_BALANCER_CONTROLLER_NETWORK_OUT_DISTRIBUTION_DETECT_AVG_DEVIATION, 0.2);
        AutoBalancerControllerConfig controllerConfig = new AutoBalancerControllerConfig(config, false);
        List<AbstractGoal> goalList = controllerConfig.getConfiguredInstances(AutoBalancerControllerConfig.AUTO_BALANCER_CONTROLLER_GOALS, AbstractGoal.class);
        for (AbstractGoal goal : goalList) {
            goalMap.put(goal.name(), goal);
        }
    }

    private Goal getGoalByResource(Resource resource) {
        Goal goal = null;
        switch (resource) {
            case NW_IN:
                goal = goalMap.get(NetworkInUsageDistributionGoal.class.getSimpleName());
                break;
            case NW_OUT:
                goal = goalMap.get(NetworkOutUsageDistributionGoal.class.getSimpleName());
                break;
            default:
                break;
        }
        return goal;
    }

    private void testSingleResourceDistributionOptimizeOneMove(Resource resource) {
        Goal goal = getGoalByResource(resource);
        Assertions.assertNotNull(goal);

        ClusterModelSnapshot cluster = new ClusterModelSnapshot();
        Broker broker0 = createBroker(cluster, RACK, 0, true);
        Broker broker1 = createBroker(cluster, RACK, 1, true);

        double load0 = 80;
        double load1 = 20;
        broker0.setLoad(resource, load0);

        broker1.setLoad(resource, load1);

        TopicPartitionReplica replica0 = createTopicPartition(cluster, 0, TOPIC_0, 0);
        TopicPartitionReplica replica1 = createTopicPartition(cluster, 0, TOPIC_2, 0);
        TopicPartitionReplica replica2 = createTopicPartition(cluster, 0, TOPIC_3, 0);
        TopicPartitionReplica replica3 = createTopicPartition(cluster, 0, TOPIC_0, 1);
        replica0.setLoad(resource, 20);
        replica1.setLoad(resource, 30);
        replica2.setLoad(resource, 15);
        replica3.setLoad(resource, 15);
        Assertions.assertEquals(load0, cluster.replicasFor(0).stream().mapToDouble(e -> e.load(resource)).sum());

        TopicPartitionReplica replica4 = createTopicPartition(cluster, 1, TOPIC_4, 0);
        TopicPartitionReplica replica5 = createTopicPartition(cluster, 1, TOPIC_2, 1);
        replica4.setLoad(resource, 15);
        replica5.setLoad(resource, 5);
        Assertions.assertEquals(load1, cluster.replicasFor(1).stream().mapToDouble(e -> e.load(resource)).sum());

        List<Action> actions = goal.optimize(cluster, goalMap.values());
        Assertions.assertNotEquals(0, actions.size());
        Assertions.assertNotNull(cluster);
        for (Broker broker : cluster.brokers()) {
            Assertions.assertTrue(goal.isBrokerAcceptable(broker));
        }
    }

    private void testSingleResourceDistributionOptimizeMultiMoveOut(Resource resource) {
        Goal goal = getGoalByResource(resource);
        Assertions.assertNotNull(goal);

        ClusterModelSnapshot cluster = new ClusterModelSnapshot();
        Broker broker0 = createBroker(cluster, RACK, 0, true);
        Broker broker1 = createBroker(cluster, RACK, 1, true);

        double load0 = 80;
        double load1 = 10;
        broker0.setLoad(resource, load0);

        broker1.setLoad(resource, load1);

        TopicPartitionReplica replica0 = createTopicPartition(cluster, 0, TOPIC_0, 0);
        TopicPartitionReplica replica1 = createTopicPartition(cluster, 0, TOPIC_0, 1);
        TopicPartitionReplica replica2 = createTopicPartition(cluster, 0, TOPIC_0, 2);
        TopicPartitionReplica replica3 = createTopicPartition(cluster, 0, TOPIC_0, 3);
        TopicPartitionReplica replica4 = createTopicPartition(cluster, 0, TOPIC_0, 4);
        TopicPartitionReplica replica5 = createTopicPartition(cluster, 0, TOPIC_0, 5);
        TopicPartitionReplica replica6 = createTopicPartition(cluster, 0, TOPIC_0, 6);
        replica0.setLoad(resource, 10);
        replica1.setLoad(resource, 20);
        replica2.setLoad(resource, 15);
        replica3.setLoad(resource, 5);
        replica4.setLoad(resource, 5);
        replica5.setLoad(resource, 10);
        replica6.setLoad(resource, 15);
        Assertions.assertEquals(load0, cluster.replicasFor(0).stream().mapToDouble(e -> e.load(resource)).sum());

        TopicPartitionReplica replica7 = createTopicPartition(cluster, 1, TOPIC_1, 0);
        TopicPartitionReplica replica8 = createTopicPartition(cluster, 1, TOPIC_1, 1);
        replica7.setLoad(resource, 5);
        replica8.setLoad(resource, 5);
        Assertions.assertEquals(load1, cluster.replicasFor(1).stream().mapToDouble(e -> e.load(resource)).sum());

        List<Action> actions = goal.optimize(cluster, goalMap.values());
        Assertions.assertNotEquals(0, actions.size());
        Assertions.assertNotNull(cluster);
        for (Broker broker : cluster.brokers()) {
            Assertions.assertTrue(goal.isBrokerAcceptable(broker));
        }
    }

    private void testSingleResourceDistributionOptimizeMultiMoveIn(Resource resource) {
        Goal goal = getGoalByResource(resource);
        Assertions.assertNotNull(goal);

        ClusterModelSnapshot cluster = new ClusterModelSnapshot();
        Broker broker0 = createBroker(cluster, RACK, 0, true);
        Broker broker1 = createBroker(cluster, RACK, 1, true);

        double load0 = 10;
        double load1 = 80;
        broker0.setLoad(resource, load0);

        broker1.setLoad(resource, load1);

        TopicPartitionReplica replica1 = createTopicPartition(cluster, 0, TOPIC_1, 0);
        TopicPartitionReplica replica2 = createTopicPartition(cluster, 0, TOPIC_1, 1);
        replica1.setLoad(resource, 5);
        replica2.setLoad(resource, 5);
        Assertions.assertEquals(load0, cluster.replicasFor(0).stream().mapToDouble(e -> e.load(resource)).sum());

        TopicPartitionReplica replica3 = createTopicPartition(cluster, 1, TOPIC_0, 0);
        TopicPartitionReplica replica4 = createTopicPartition(cluster, 1, TOPIC_0, 1);
        TopicPartitionReplica replica5 = createTopicPartition(cluster, 1, TOPIC_0, 2);
        TopicPartitionReplica replica6 = createTopicPartition(cluster, 1, TOPIC_0, 3);
        TopicPartitionReplica replica7 = createTopicPartition(cluster, 1, TOPIC_0, 4);
        TopicPartitionReplica replica8 = createTopicPartition(cluster, 1, TOPIC_0, 5);
        TopicPartitionReplica replica9 = createTopicPartition(cluster, 1, TOPIC_0, 6);
        replica3.setLoad(resource, 10);
        replica4.setLoad(resource, 20);
        replica5.setLoad(resource, 15);
        replica6.setLoad(resource, 5);
        replica7.setLoad(resource, 5);
        replica8.setLoad(resource, 10);
        replica9.setLoad(resource, 15);
        Assertions.assertEquals(load1, cluster.replicasFor(1).stream().mapToDouble(e -> e.load(resource)).sum());

        List<Action> actions = goal.optimize(cluster, goalMap.values());
        Assertions.assertNotEquals(0, actions.size());
        Assertions.assertNotNull(cluster);
        for (Broker broker : cluster.brokers()) {
            Assertions.assertTrue(goal.isBrokerAcceptable(broker));
        }
    }

    @Test
    public void testSingleResourceDistributionOptimizeOneMove() {
        testSingleResourceDistributionOptimizeOneMove(Resource.NW_IN);
        testSingleResourceDistributionOptimizeOneMove(Resource.NW_OUT);
    }

    @Test
    public void testSingleResourceDistributionOptimizeMultiMoveOut() {
        testSingleResourceDistributionOptimizeMultiMoveOut(Resource.NW_IN);
        testSingleResourceDistributionOptimizeMultiMoveOut(Resource.NW_OUT);
        testSingleResourceDistributionOptimizeMultiMoveIn(Resource.NW_IN);
        testSingleResourceDistributionOptimizeMultiMoveIn(Resource.NW_OUT);
    }

    private void testMultiGoalOptimizeWithOneToOneReplicaSwap(Resource resource) {
        Goal goal = getGoalByResource(resource);
        Assertions.assertNotNull(goal);

        ClusterModelSnapshot cluster = new ClusterModelSnapshot();
        Broker broker0 = createBroker(cluster, RACK, 0, true);
        Broker broker1 = createBroker(cluster, RACK, 1, true);

        broker0.setLoad(Resource.NW_IN, 90);
        broker0.setLoad(Resource.NW_OUT, 50);

        broker1.setLoad(Resource.NW_IN, 20);
        broker1.setLoad(Resource.NW_OUT, 90);

        TopicPartitionReplica replica0 = createTopicPartition(cluster, 0, TOPIC_0, 0);
        TopicPartitionReplica replica1 = createTopicPartition(cluster, 0, TOPIC_1, 0);
        replica0.setLoad(Resource.NW_IN, 40);
        replica0.setLoad(Resource.NW_OUT, 30);
        replica1.setLoad(Resource.NW_IN, 50);
        replica1.setLoad(Resource.NW_OUT, 20);
        Assertions.assertEquals(90, cluster.replicasFor(0).stream().mapToDouble(e -> e.load(Resource.NW_IN)).sum());
        Assertions.assertEquals(50, cluster.replicasFor(0).stream().mapToDouble(e -> e.load(Resource.NW_OUT)).sum());

        TopicPartitionReplica replica2 = createTopicPartition(cluster, 1, TOPIC_0, 1);
        TopicPartitionReplica replica3 = createTopicPartition(cluster, 1, TOPIC_1, 1);
        replica2.setLoad(Resource.NW_IN, 5);
        replica2.setLoad(Resource.NW_OUT, 50);
        replica3.setLoad(Resource.NW_IN, 15);
        replica3.setLoad(Resource.NW_OUT, 40);
        Assertions.assertEquals(20, cluster.replicasFor(1).stream().mapToDouble(e -> e.load(Resource.NW_IN)).sum());
        Assertions.assertEquals(90, cluster.replicasFor(1).stream().mapToDouble(e -> e.load(Resource.NW_OUT)).sum());

        List<Action> actions = goal.optimize(cluster, goalMap.values());
        Assertions.assertNotEquals(0, actions.size());
        Assertions.assertNotNull(cluster);
        for (Broker broker : cluster.brokers()) {
            Assertions.assertTrue(goal.isBrokerAcceptable(broker));
        }
    }

    @Test
    public void testMultiGoalOptimizeWithOneToOneReplicaSwap() {
        testMultiGoalOptimizeWithOneToOneReplicaSwap(Resource.NW_IN);
        testMultiGoalOptimizeWithOneToOneReplicaSwap(Resource.NW_OUT);
    }

}
