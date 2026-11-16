package archprobes.adapter.in;

import archprobes.domain.ProbeRepository;

/** Sonda: un controlador que habla con un repositorio sin pasar por un puerto primario. */
public class FatController {
    private final ProbeRepository repository;

    FatController(ProbeRepository repository) { this.repository = repository; }

    String get(String id) { return repository.find(id); }
}
