package dev.personal.autolab

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator

/**
 * Punto de entrada de AutoLab frente al host de Android Auto.
 *
 * ALLOW_ALL_HOSTS_VALIDATOR acepta cualquier host (DHU, cualquier version de Android Auto).
 * Esto es intencional: AutoLab es una app de laboratorio, nunca se distribuye ni se firma
 * para produccion, asi que no necesitamos restringir que hosts pueden conectarse.
 */
class AutoLabCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(sessionInfo: SessionInfo): Session {
        return AutoLabSession()
    }
}
