/**
 * Copyright 2018 The original authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.dekorate.openshift.manifest;

import java.util.Optional;

import io.dekorate.AbstractKubernetesManifestGenerator;
import io.dekorate.ConfigurationRegistry;
import io.dekorate.Logger;
import io.dekorate.LoggerFactory;
import io.dekorate.ResourceRegistry;
import io.dekorate.config.ConfigurationSupplier;
import io.dekorate.kubernetes.config.ConfigKey;
import io.dekorate.kubernetes.config.Configuration;
import io.dekorate.kubernetes.config.Container;
import io.dekorate.kubernetes.config.ImageConfiguration;
import io.dekorate.kubernetes.config.Label;
import io.dekorate.kubernetes.configurator.ApplyDeployToApplicationConfiguration;
import io.dekorate.kubernetes.decorator.AddCommitIdAnnotationDecorator;
import io.dekorate.kubernetes.decorator.AddInitContainerDecorator;
import io.dekorate.kubernetes.decorator.AddLabelDecorator;
import io.dekorate.kubernetes.decorator.AddServiceResourceDecorator;
import io.dekorate.kubernetes.decorator.AddVcsUrlAnnotationDecorator;
import io.dekorate.kubernetes.decorator.ApplyHeadlessDecorator;
import io.dekorate.kubernetes.decorator.ApplyReplicasToDeploymentDecorator;
import io.dekorate.kubernetes.decorator.ApplyReplicasToStatefulSetDecorator;
import io.dekorate.kubernetes.decorator.DeploymentResourceFactory;
import io.dekorate.kubernetes.decorator.RemoveAnnotationDecorator;
import io.dekorate.kubernetes.decorator.StatefulSetResourceFactory;
import io.dekorate.openshift.OpenshiftAnnotations;
import io.dekorate.openshift.OpenshiftLabels;
import io.dekorate.openshift.config.EditableOpenshiftConfig;
import io.dekorate.openshift.config.OpenshiftConfig;
import io.dekorate.openshift.config.OpenshiftConfigBuilder;
import io.dekorate.openshift.decorator.AddRouteDecorator;
import io.dekorate.openshift.decorator.ApplyDeploymentTriggerDecorator;
import io.dekorate.openshift.decorator.ApplyReplicasToDeploymentConfigDecorator;
import io.dekorate.option.config.VcsConfig;
import io.dekorate.project.ApplyProjectInfo;
import io.dekorate.project.Project;
import io.dekorate.utils.Annotations;
import io.dekorate.utils.Git;
import io.dekorate.utils.Labels;
import io.dekorate.utils.Strings;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;

public class OpenshiftManifestGenerator extends AbstractKubernetesManifestGenerator<OpenshiftConfig> {

  private static final String OPENSHIFT = "openshift";

  public static final ConfigKey<String> RUNTIME_TYPE = new ConfigKey<>("RUNTIME_TYPE", String.class);

  private final Logger LOGGER = LoggerFactory.getLogger();

  public OpenshiftManifestGenerator(ResourceRegistry resourceRegistry, ConfigurationRegistry configurationRegistry) {
    super(resourceRegistry, configurationRegistry);
    resourceRegistry.groups().putIfAbsent(OPENSHIFT, new KubernetesListBuilder());
  }

  @Override
  public int order() {
    return 300;
  }

  @Override
  public String getKey() {
    return OPENSHIFT;
  }

  public void generate(OpenshiftConfig config) {
    LOGGER.info("Processing openshift configuration.");
    initializeRegistry(config);

    if (config.isHeadless()) {
      resourceRegistry.decorate(OPENSHIFT, new ApplyHeadlessDecorator(config.getName()));
    }

    for (Container container : config.getInitContainers()) {
      resourceRegistry.decorate(OPENSHIFT, new AddInitContainerDecorator(config.getName(), container));
    }

    if (config.getPorts().length > 0) {
      resourceRegistry.decorate(OPENSHIFT, new AddServiceResourceDecorator(config));
    }

    addDecorators(OPENSHIFT, config);
  }

  @Override
  public ConfigurationSupplier<OpenshiftConfig> getFallbackConfig() {
    Project p = getProject();
    return new ConfigurationSupplier<OpenshiftConfig>(new OpenshiftConfigBuilder()
        .accept(new ApplyDeployToApplicationConfiguration()).accept(new ApplyProjectInfo(p)));
  }

  protected void addDecorators(String group, OpenshiftConfig config) {
    super.addDecorators(group, config);
    ImageConfiguration imageConfig = getImageConfiguration(config);

    if (config.getReplicas() != 1) {
      if (StatefulSetResourceFactory.KIND.equalsIgnoreCase(config.getDeploymentKind())) {
        resourceRegistry.decorate(group, new ApplyReplicasToStatefulSetDecorator(config.getName(), config.getReplicas()));
      } else if (DeploymentResourceFactory.KIND.equalsIgnoreCase(config.getDeploymentKind())) {
        resourceRegistry.decorate(group, new ApplyReplicasToDeploymentDecorator(config.getName(), config.getReplicas()));
      } else {
        resourceRegistry.decorate(group, new ApplyReplicasToDeploymentConfigDecorator(config.getName(), config.getReplicas()));
      }
    }
    resourceRegistry.decorate(group,
        new ApplyDeploymentTriggerDecorator(config.getName(), imageConfig.getName() + ":" + imageConfig.getVersion()));
    resourceRegistry.decorate(group, new AddRouteDecorator(config));

    if (config.hasAttribute(RUNTIME_TYPE)) {
      resourceRegistry.decorate(group, new AddLabelDecorator(config.getName(),
          new Label(OpenshiftLabels.RUNTIME, config.getAttribute(RUNTIME_TYPE), new String[0])));
    }
    resourceRegistry.decorate(group, new RemoveAnnotationDecorator(config.getName(), Annotations.VCS_URL));

    Project project = getProject();
    Optional<VcsConfig> vcsConfig = configurationRegistry.get(VcsConfig.class);
    String remote = vcsConfig.map(VcsConfig::getRemote).orElse(Git.ORIGIN);
    boolean httpsPrefered = vcsConfig.map(VcsConfig::isHttpsPreferred).orElse(false);

    String vcsUrl = project.getScmInfo() != null && Strings.isNotNullOrEmpty(project.getScmInfo().getRemote().get(Git.ORIGIN))
        ? Git.getRemoteUrl(project.getRoot(), remote, httpsPrefered).orElse(Labels.UNKNOWN)
        : Labels.UNKNOWN;

    resourceRegistry.decorate(group, new AddVcsUrlAnnotationDecorator(config.getName(), OpenshiftAnnotations.VCS_URL, vcsUrl));
    resourceRegistry.decorate(group, new AddCommitIdAnnotationDecorator());
  }

  public boolean accepts(Class<? extends Configuration> type) {
    return type.equals(OpenshiftConfig.class) || type.equals(EditableOpenshiftConfig.class);
  }
}
