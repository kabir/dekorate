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
package io.dekorate.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.dekorate.helm.annotation.HelmChart;
import io.dekorate.helm.annotation.ValueReference;
import io.dekorate.openshift.annotation.OpenshiftApplication;

@HelmChart(name = "myOcpChart",
  values = { @ValueReference(property = "helmOnOpenshiftExample.not-found", jsonPaths = "$.metadata.not-found"),
    @ValueReference(property = "helmOnOpenshiftExample.commit-id", jsonPaths = "$[?(@.kind == 'DeploymentConfig')]['spec']['template']['metadata']['annotations']['app.dekorate.io/commit-id']"),
    @ValueReference(property = "helmOnOpenshiftExample.vcs-url", jsonPaths = "$[?(@.kind == 'DeploymentConfig')]['spec']['template']['metadata']['annotations']['app.dekorate.io/vcs-url']", value = "Overridden"),
    @ValueReference(property = "helmOnOpenshiftExample.vcs-url", jsonPaths = "$[?(@.kind == 'DeploymentConfig')]['spec']['template']['metadata']['annotations']['app.dekorate.io/vcs-url']", value = "Only for DEV!", profile = "dev")
})
@SpringBootApplication
public class Main {

  public static void main(String[] args) {
    SpringApplication.run(Main.class, args);
  }
}
