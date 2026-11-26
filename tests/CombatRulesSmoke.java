import dev.hydev.spectrecore.core.CombatRules;
public class CombatRulesSmoke {
    public static void main(String[] args) {
        assert CombatRules.addFury(95, 12) == 100;
        assert CombatRules.addFury(0, -2) == 0;
        assert CombatRules.addFury(22, 6) == 28;
        assert Math.abs(CombatRules.comboMultiplier(0, 4, .03) - 1.0) < .000001;
        assert Math.abs(CombatRules.comboMultiplier(4, 4, .03) - 1.12) < .000001;
        assert Math.abs(CombatRules.comboMultiplier(9, 4, .03) - 1.12) < .000001;
        assert Math.abs(CombatRules.oathMultiplier(false, .15, 0) - 1.15) < .000001;
        assert Math.abs(CombatRules.oathMultiplier(true, .15, 0) - 1.0) < .000001;
        assert Math.abs(CombatRules.oathMultiplier(true, .15, .10) - 1.10) < .000001;
        assert Math.abs(CombatRules.oathMultiplier(false, .15, .10) - 1.25) < .000001;
        assert Math.abs(CombatRules.dragonMultiplier(false, false, false, .03, .06) - 1.03) < .000001;
        assert Math.abs(CombatRules.dragonMultiplier(false, false, true, .03, .06) - 1.09) < .000001;
        assert Math.abs(CombatRules.dragonMultiplier(true, true, true, .03, .06) - 1.0) < .000001;
        assert Math.abs(CombatRules.dragonMultiplier(true, false, true, .03, .06) - 1.06) < .000001;
        assert CombatRules.bonusPercent(1.0) == 0;
        assert CombatRules.bonusPercent(.9) == 0;
        assert CombatRules.bonusPercent(Double.NaN) == 0;
        assert CombatRules.bonusPercent(1.06) == 6;
        assert CombatRules.bonusPercent(1.012345) == 1.23;
        assert CombatRules.bonusPercent(CombatRules.oathMultiplier(true, .15, 0)) == 0;
        assert CombatRules.bonusPercent(CombatRules.oathMultiplier(true, .15, .10)) == 10;
        assert CombatRules.extend(140, 200, 12) == 152;
        assert CombatRules.extend(197, 200, 12) == 200;
        assert CombatRules.blessingEnd(200, 240, false) == 200;
        assert CombatRules.blessingEnd(200, 240, true) == 240;
        System.out.println("OK — fury, combo, Voto AuraSkills fallback, bonus caps, duration limits");
    }
}
