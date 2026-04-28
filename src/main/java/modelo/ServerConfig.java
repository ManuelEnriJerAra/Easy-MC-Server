
/*
 * Fichero: ServerConfig.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 * Fecha: 17/01/2026
 *
 * Descripción:
 * Esta clase contiene la dirección y RAM asignada al servidor. Es lo mínimo necesario para identificar al servidor.
 *
 * */

package modelo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor

public class ServerConfig {
    private int ramInit; // ram inicial dedicada al servidor
    private int ramMax; // ram máxima disponible para el servidor
    private int puerto; // en qué puerto se va a ejecutar el servidor

    public ServerConfig() {
        this.ramInit = 1024;
        this.ramMax = 2048;
        this.puerto = 25565;
    }

    public void setRamInit(int ramInit) {
        if (ramInit > this.ramMax || ramInit < 0) throw  new IllegalArgumentException("Min RAM no válida");
        this.ramInit = ramInit;
    }
    public void setRamMax(int ramMax) {
        if (ramMax < this.ramInit) throw new IllegalArgumentException("Max RAM no válida");
        this.ramMax = ramMax;
    }
}
