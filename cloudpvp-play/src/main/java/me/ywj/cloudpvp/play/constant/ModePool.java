package me.ywj.cloudpvp.play.constant;

import me.ywj.cloudpvp.core.model.play.Mode;
import me.ywj.cloudpvp.core.model.play.Type;

import java.util.HashMap;
import java.util.List;

/**
 * ModePool
 *
 * @author sheip9
 * @since 2024/10/16 10:58
 */
public class ModePool {
    public static final HashMap<String, List<Type>> MODE_LIST = new HashMap<>();
    static {
        List<Type> cs2Types = List.of(new Type("TEST-CS2", "测试1", List.of(new Mode("TEST-CS2", "测试模式", "测试模式，用于初期平台技术验证"))));
        List<Type> csgoTypes = List.of(new Type("TEST-CSGO", "测试2", List.of(new Mode("TEST-CSGO", "测试模式", "测试模式，用于初期平台技术验证"))));
        MODE_LIST.put(GamePool.CS2.getKey(), cs2Types);
        MODE_LIST.put(GamePool.CSGO.getKey(), csgoTypes);
    }
}
