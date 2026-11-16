package com.socialhub.demo.domain;

import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** De dónde salen los datos de ejemplo. */
@SecondaryPort
public interface DemoDataSource {

    DemoData read();
}
