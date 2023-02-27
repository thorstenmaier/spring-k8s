# Simple Spring Boot Graal VM Native Image K8s example

This example shows how to implement a Spring Boot application up and get it up and running in a Kubernetes Cluster.

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

record Customer (@Id Integer id, String name) {
}
```

Create schema.sql file in `src/main/resources`:

```sql
create table customer (
    id serial primary key,
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

## Steps to get it up and running in Docker

With the `mvnw spring-boot:build-image` command, we can now very easily create a Docker image from this Spring Boot application. 
However, we are dependent on the local build environment (e.g. the local Java version). 
Since we want to create a native image with GraalVM later anyway, let's use this VM directly in a Docker container to build the application.
First we create a classic standalone fat JAR in the target directory (`mvn clean package`).

```shell
docker run -v ${PWD}:/home/app -w /home/app vegardit/graalvm-maven:22.3.1-java17 mvn clean package
```

However, using the same technique, we are also able to create a Docker image with the help of this helper image:

```shell
docker run -v ${PWD}:/home/app -w /home/app -v /var/run/docker.sock:/var/run/docker.sock vegardit/graalvm-maven:22.3.1-java17 mvn spring-boot:build-image
```

### Create deployment and service yaml from scratch

```shell
kubectl create deployment --image=docker.io/library/spring-k8s:0.0.1-SNAPSHOT --dry-run=client -o yaml spring-k8s-deployment > k8s/deployment.yaml
```

```shell
kubectl create service clusterip spring-k8s-deployment --tcp 8080:8080 -o yaml --dry-run=client > k8s/service.yaml
```

```shell
kubectl apply -f k8s/.
```

ClusterIP service is not reachable from outside the cluster. Therefore start a port forward to access the service

```shell
kubectl port-forward service/spring-k8s-deployment 8080:8080
```
