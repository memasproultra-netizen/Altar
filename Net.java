package com.rpserver.altarmod.net;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class Net {
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar reg = event.registrar("1");
        reg.playToServer(C2SAction.TYPE, C2SAction.CODEC, C2SAction::handle);
        reg.playToClient(S2CState.TYPE, S2CState.CODEC, S2CState::handle);
    }
}
