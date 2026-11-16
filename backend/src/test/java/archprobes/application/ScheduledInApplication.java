package archprobes.application;

/** Sonda: reloj dentro de la aplicación. */
public class ScheduledInApplication {
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 1000)
    void tick() {}
}
