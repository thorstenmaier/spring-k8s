# Spring Boot Graal VM Native Image K8s example

This example shows step by step how to implement a Spring Boot application and get it up and running in a Kubernetes 
Cluster in a "cloud native" way.

## Create a green field Spring Boot Application

Select the following dependencies:

- Spring Reactive Web (Spring Webflux)
- Spring R2DBC
- H2
- Spring Boot Actuator
- Lombok
- Spring Native

## Implement persistence and web layer

```java

@RestController
@RequiredArgsConstructor
class CustomerHttpController {

    private final CustomerRepository customerRepository;

    @GetMapping("/customers")
    Flux<Customer> get() {
        return this.customerRepository.findAll();
    }
}

interface CustomerRepository extends ReactiveCrudRepository<Customer, Integer> {
}

record Customer(@Id Integer id, String name) {
}
```

Create schema.sql file in `src/main/resources`:

```sql
create table customer
(
    id   serial primary key,
    name varchar(255) not null
);
```

Create an application listener to get some data into the database:

```java

@SpringBootApplication
public class SpringK8sApplication {
    @Bean
    ApplicationListener<ApplicationStartedEvent> applicationStartedEventApplicationListener(CustomerRepository customerRepository) {
        return event -> Flux.just("Simon", "Thomas")
                .map(s -> new Customer(null, s))
                .flatMap(customerRepository::save)
                .subscribe(System.out::println);
    }
}
```

- Start application
- Open browser
- Test web interface `http://localhost:8080/customers`

## Steps to get it up and running in Docker or Kubernetes

With the `mvnw spring-boot:build-image` command, we can now very easily create a Docker image from this Spring Boot
application.
However, we are dependent on the local build environment (e.g. the local Java version).
Since we want to create a native image with GraalVM later anyway, let's use this VM directly in a Docker container to
build the application.
First we create a classic standalone fat JAR in the target directory (`mvn clean package`). Hint: `${PWD}` is the PowerShell syntax for getting the current folder. Use alternatives for other shells! 

```shell
docker run -v ${PWD}:/home/app -w /home/app vegardit/graalvm-maven:22.3.1-java17 mvn clean package
```

However, using the same technique, we are also able to create a Docker image with the help of this helper image:

```shell
docker run -v ${PWD}:/home/app -w /home/app -v /var/run/docker.sock:/var/run/docker.sock vegardit/graalvm-maven:22.3.1-java17 mvn spring-boot:build-image
```

We'll be using this build a few more times, so let's tweak it a bit to speed up further builds using our local Maven repository. 
Note: `${HOME}` is the PowerShell syntax to get the user's home location. Use alternatives for other shells!

```shell
docker run -v ${PWD}:/home/app -w /home/app -v /var/run/docker.sock:/var/run/docker.sock -v ${HOME}/.m2/repository:/root/.m2/repository vegardit/graalvm-maven:22.3.1-java17 mvn spring-boot:build-image
```

Please note that the name of the Docker image is displayed at the end of the build. We need this name to launch the
image in Docker or Kubernetes.

Now we are able to run the application in docker:

```shell
docker run -p 8080:8080 docker.io/library/spring-k8s:0.0.1-SNAPSHOT
```

or in Kubernetes:

```shell
kubectl run --image=docker.io/library/spring-k8s:0.0.1-SNAPSHOT my-app
kubectl logs my-app
```

Please note that a pod is not reachable from outside the cluster without further configuration. So it is not possible
to access the pod via browser at the moment.

## Now it gets exciting: optimization capabilities

So far, it has been very easy to get a Spring application running in a Kubernetes cluster. However, the current
operating mode is not really "cloud native". Why?

- The application should tell Kubernetes when it is already after startup to receive the first requests. Otherwise,
  the first requests could be forwarded to the application during the initialization phase. (**Readiness**)
- The application knows best how it is feeling and whether normal operation can still be guaranteed.
  If this is not the case, Kubernetes should be informed so that Kubernetes is able to restart the container. (**Liveness**)
- How can the application tell that it is still working in principle, but is currently overloaded and therefore (**State change**)
  does not want to accept any more requests at the moment?
- How can Kubernetes tell the application to shut down properly without losing data or currently active requests? (**Graceful shutdown**)
- Scaling up and down requires fast start-up times. Our application takes about 2 seconds to start. That's not much,
  but to be honest, our application is still very small at the moment. Wouldn't it be great if we could improve the
  start-up time by a factor of 30? (**Graal VM Native Image**)

## Readiness and liveness of the application

The readiness state in Kubernetes indicates whether an application is "ready" to receive requests or not.
The liveness state in Kubernetes indicates whether an application has to be restarted by the cluster or not.

We have already included Spring Boot Actuator as a dependency in the Spring application. This module offers the 
possibility to make liveness and readiness states accessible. This requires an adjustment of the "application.properties".

```properties
management.endpoints.web.exposure.include=*
management.endpoint.health.show-details=always
management.endpoint.health.probes.enabled=true
```

Start the application and open `http://localhost:8080/actuator/health` to see the change.

Liveness and readiness probes are specified declarative in Kubernetes. To do this, we first generate a template for 
the upcoming deployment and then adapt this content.

```shell
kubectl create deployment --image=docker.io/library/spring-k8s:0.0.1-SNAPSHOT --dry-run=client -o yaml spring-k8s-deployment > k8s/deployment.yaml
```

Add the liveness and readiness probes to the deployment.yaml:

```yaml
spec:
  template:
    spec:
      containers:
        - name: spring-k8s
          image: docker.io/library/spring-k8s:0.0.1-SNAPSHOT
          livenessProbe:
            httpGet:
              port: 8080
              path: /actuator/health/liveness
            initialDelaySeconds: 5
          readinessProbe:
            httpGet:
              port: 8080
              path: /actuator/health/readiness
```

Let's also create a NodePort service for easy access to the ports. We can create a service template with the following command:

```shell
kubectl create service nodeport --tcp=8080:8080 --node-port=30008 --dry-run=client -o yaml spring-k8s-service > spring-k8s-service.yaml
``` 

Open the created yaml file `spring-k8s-service.yaml` in an editor to adjust the pod selector:

```yaml
  selector:
    app: spring-k8s-deployment
```

Apply the deployment and service yaml in the K8s cluster:

```shell
kubectl apply -f spring-k8s-deployment.yaml
kubectl apply -f spring-k8s-service.yaml
```

Open `http://localhost:30008/customers`.

## Publish Readiness state

```java
@SneakyThrows
@GetMapping("/customers")
public Flux<Customer> getAllCustomers(@RequestParam(required = false, defaultValue = "false") boolean slow) {
  if (slow) {
    AvailabilityChangeEvent.publish(context, ReadinessState.REFUSING_TRAFFIC);
    Thread.sleep(120_000);
    AvailabilityChangeEvent.publish(context, ReadinessState.ACCEPTING_TRAFFIC);
  }

  return customerRepository.findAll();
}
```

* Increase number of replicas
* Redeploy application
* `kubectl get pod -w`
* Call `http://localhost:30008/customers?slow=true`

## Use Native Images

* Show startup time `kubectl get pod <<pod-name>>`
* Add plugin to `pom.xml`

```xml
<build>
  <pluginManagement>
      <plugins>
          <plugin>
              <groupId>org.springframework.boot</groupId>
              <artifactId>spring-boot-maven-plugin</artifactId>
              <configuration>
                  <image>
                      <builder>paketobuildpacks/builder:tiny</builder>
                      <env>
                          <BP_NATIVE_IMAGE>true</BP_NATIVE_IMAGE>
                      </env>
                  </image>
              </configuration>
          </plugin>
      </plugins>
  </pluginManagement>
</build>
```

* Rerun build (takes much more time) and redeploy application

## Graceful shudown (optional)

Add the following settings to `application.properties`.

```properties
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=30s
``` 