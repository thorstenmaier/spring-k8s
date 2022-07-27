package com.example.springk8s;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.data.annotation.Id;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@SpringBootApplication
public class SpringK8sApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringK8sApplication.class, args);
    }

    @Bean
    ApplicationListener<AvailabilityChangeEvent<?>> availabilityChangeEventApplicationListener() {
        return event -> System.out.println(event.getResolvableType() + ": " + event.getState());
    }

    @Bean
    ApplicationListener<ApplicationStartedEvent> applicationStartedEventApplicationListener(CustomerRepository customerRepository) {
        return event -> Flux.just("Simon", "Thomas")
                .map(s -> new Customer(null, s))
                .flatMap(customerRepository::save)
                .subscribe(System.out::println);
    }

}

@RestController
@RequiredArgsConstructor
class AvailabilityHttpController {
    private final ApplicationContext context;

    @GetMapping("/down")
    void down() {
        AvailabilityChangeEvent.publish(this.context, LivenessState.BROKEN);
    }

    @GetMapping("/slow")
    void slow() throws InterruptedException {
        Thread.sleep(10_000);
    }
}

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
