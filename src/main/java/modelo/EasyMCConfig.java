package modelo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EasyMCConfig {
    private String temaClassName;
    private Integer estadisticasRangoSegundos;
    private Boolean estadisticasPersistenciaActiva;
    private Integer estadisticasVentanaRecienteSegundos;
    private Integer estadisticasResolucionHistoricaSegundos;
    private Boolean jugadoresListaCompacta;
    private Boolean consolaVistaSimple;

    public EasyMCConfig() {
    }

    public EasyMCConfig(String temaClassName) {
        this.temaClassName = temaClassName;
    }

    public EasyMCConfig(String temaClassName, Integer estadisticasRangoSegundos) {
        this.temaClassName = temaClassName;
        this.estadisticasRangoSegundos = estadisticasRangoSegundos;
    }

    public EasyMCConfig(String temaClassName,
                        Integer estadisticasRangoSegundos,
                        Boolean estadisticasPersistenciaActiva,
                        Integer estadisticasVentanaRecienteSegundos,
                        Integer estadisticasResolucionHistoricaSegundos,
                        Boolean jugadoresListaCompacta,
                        Boolean consolaVistaSimple) {
        this.temaClassName = temaClassName;
        this.estadisticasRangoSegundos = estadisticasRangoSegundos;
        this.estadisticasPersistenciaActiva = estadisticasPersistenciaActiva;
        this.estadisticasVentanaRecienteSegundos = estadisticasVentanaRecienteSegundos;
        this.estadisticasResolucionHistoricaSegundos = estadisticasResolucionHistoricaSegundos;
        this.jugadoresListaCompacta = jugadoresListaCompacta;
        this.consolaVistaSimple = consolaVistaSimple;
    }
}
