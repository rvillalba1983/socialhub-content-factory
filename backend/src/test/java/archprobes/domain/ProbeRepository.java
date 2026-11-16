package archprobes.domain;

/** Sonda: un puerto de salida (repositorio) cualquiera. */
@org.jmolecules.architecture.hexagonal.SecondaryPort
public interface ProbeRepository {
    String find(String id);
}
