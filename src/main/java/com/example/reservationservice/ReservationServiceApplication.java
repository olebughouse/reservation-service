package com.example.reservationservice;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.reactivestreams.Publisher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.http.MediaType;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;


@Controller
@RequiredArgsConstructor
class RSocketGreetingController {

    private final IntervalMessageProducer intervalMessageProducer;

    @MessageMapping("greetings")
    Flux<GreetingsResponse> greet(GreetingsRequest greetingsRequest) {
        return this.intervalMessageProducer.produce(greetingsRequest);
    }
}

//@EnableR2dbcRepositories
//@Configuration
//class R2dbcConfig extends AbstractR2dbcConfiguration {
//    @Override
//    public ConnectionFactory connectionFactory() {
//        return new PostgresqlConnectionFactory(
//                PostgresqlConnectionConfiguration.builder()
//                        .username("postgres")
//                        .password("example")
//                        .host("localhost")
//                        .database("reservation")
//                        .build()
//        );
//    }
//}

//interface ReservationRepository extends ReactiveCrudRepository<Reservation, String> {
//}

interface ReservationRepository extends ReactiveCrudRepository<Reservation, Integer> {
}

@SpringBootApplication
public class ReservationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReservationServiceApplication.class, args);
    }

    @Bean
    RouterFunction <ServerResponse> route (ReservationRepository rr) {
//        return RouterFunctions.route()
//                .GET("reservations", new HandlerFunction<ServerResponse>() {
//                    @Override
//                    public Mono<ServerResponse> handle(ServerRequest serverRequest) {
//                        return ServerResponse.ok().body(rr.findAll(), Reservation.class);
//                    }
//                })
//                .build();

        return RouterFunctions.route()
                .GET("reservationsNew", serverRequest -> ServerResponse.ok().body(rr.findAll(), Reservation.class))
                .build();
    }
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class GreetingsRequest {
    private String name;
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class GreetingsResponse {
    private String greeting;
}

@Component
class IntervalMessageProducer {

    Flux <GreetingsResponse> produce(GreetingsRequest name) {
        return Flux.fromStream(Stream.generate(() -> "Hello " + name.getName() + " @ " + Instant.now()))
                .map(GreetingsResponse::new)
                .delayElements(Duration.ofSeconds(1));
    }
}

@RestController
@RequiredArgsConstructor
class ReservationRestController {
    private final ReservationRepository reservationRepository;
    private final IntervalMessageProducer intervalMessageProducer;

    @GetMapping("reservations")
    Publisher<Reservation> reservationPublisher() {
        return this.reservationRepository.findAll();
    }

    @GetMapping (produces = MediaType.TEXT_EVENT_STREAM_VALUE, value = "/sse/{n}")
    Publisher <GreetingsResponse> stringPublisher(@PathVariable String n) {
        return this.intervalMessageProducer.produce(new GreetingsRequest(n));
    }
}


@Component
@RequiredArgsConstructor
@Log4j2
class SampleDataInitializer {

    private final ReservationRepository reservationRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
//        Flux<String> names = Flux.just("Josh", "Olga", "Coronelia");
//        Flux<Reservation> reservationFlux = names.map(name -> new Reservation(null, name));
//        Publisher<Reservation> saved = reservationFlux.flatMap(this.reservationRepository::save);

        var saved = Flux
                .just("Josh", "Olga", "Coronelia")
                .map(name -> new Reservation(null, name))
                .flatMap(this.reservationRepository::save);

        reservationRepository
                .deleteAll()
                .thenMany(saved)
                .thenMany(this.reservationRepository.findAll())
                .subscribe(log::info);
    }
}

@Document
@Data
@AllArgsConstructor
@NoArgsConstructor
class Reservation {

    @Id
    private String id;

//    @Id
//    private Integer id;

    private String name;
}
