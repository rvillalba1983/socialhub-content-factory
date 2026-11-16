package archprobes.beta;

import archprobes.alpha.application.Orchestrator;
import archprobes.alpha.domain.Secret;

/** Sonda: un módulo que usa el dominio y la aplicación de otro. */
public class Intruder {
    Secret secret = new Secret();
    Orchestrator orchestrator = new Orchestrator();
}
