# Simple Spring Boot Graal VM Native Image K8s example

This little example shows who to get a Spring Boot application up and running in a Minikube Kubernetes Cluster.

## Used Spring Modules

- Spring Native
- Spring Reactive Web
- Spring R2DBC
- H2
- Spring Boot Actuator
- Lombok

## Steps to get it up and running in K8s cluster

### Start Minikube

```shell
minikube delete
minikube start --driver virtualbox --no-vtx-check --memory 8192 --cpus 4
```

### Build Docker image

```shell
minikube docker-env
@FOR /f "tokens=*" %i IN ('minikube -p minikube docker-env') DO @%i
```

```shell
mvnw spring-boot:build-image
```

### Deploy to Minikube

```shell
minikube kubectl -- apply -f deployment.yml
```

```shell
minikube kubectl -- apply -f service.yml
```

## Access in browser

Call `minikube docker-env` to get the ip address.

Open browser and navigate to `http://[ip]:32055/`