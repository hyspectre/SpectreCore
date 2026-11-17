import dev.hydev.spectrecore.core.CombatRules;
public class CombatRulesSmoke {
    public static void main(String[] args) {
        assert CombatRules.addFury(95, 12) == 100;
        assert CombatRules.addFury(0, -2) == 0;
        assert CombatRules.addFury(22, 6) == 28;
        assert Math.abs(CombatRules.comboMultiplier(0, 4, .03) - 1.0) < .000001;
        assert Math.abs(CombatRules.comboMultiplier(4, 4, .03) - 1.12) < .000001;
        assert Math.abs(CombatRules.comboMultiplier(9, 4, .03) - 1.12) < .000001;
        assert CombatRules.extend(140, 200, 12) == 152;
        assert CombatRules.extend(197, 200, 12) == 200;
        assert CombatRules.blessingEnd(200, 240, false) == 200;
        assert CombatRules.blessingEnd(200, 240, true) == 240;
        System.out.println("OK — fury, combo, bonus caps, duration limits");
    }
}
