package archprobes.adapter.in;

/** Sonda: un adaptador de entrada que abre una transacción. */
public class TransactionalInInbound {
    @org.springframework.transaction.annotation.Transactional
    void work() {}
}
