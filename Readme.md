# Simple Spring Boot Graal VM Native Image K8s example

This little example shows who to get a Spring Boot application up and running in a Minikube Kubernetes Cluster.

## Used Spring Modules

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

You may need to choose a different driver such as Docker or Hyperv.

### Build Docker image

```shell
minikube docker-env
@FOR /f "tokens=*" %i IN ('minikube -p minikube docker-env') DO @%i
```

```shell
mvnw spring-boot:build-image
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

### Alternative: Deploy predefined yaml files to Minikube

```shell
minikube kubectl -- apply -f deployment.yml
```

```shell
minikube kubectl -- apply -f service.yml
```

## Access in browser

Call `minikube docker-env` to get the ip address.

Open browser and navigate to `http://[ip]:32055/`