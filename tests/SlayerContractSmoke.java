import dev.hydev.spectrecore.systems.SlayerContractRules;
import static dev.hydev.spectrecore.systems.SlayerContractRules.Refusal.*;

public final class SlayerContractSmoke {
    private static int checks;
    private static void check(boolean value, String reason) { checks++; if (!value) throw new AssertionError(reason); }
    public static void main(String[] args) {
        check(SlayerContractRules.validateStart(false,1,false,true,200)==DISABLED,"off blocks purchase");
        check(SlayerContractRules.validateStart(true,0,false,true,200)==INVALID_TIER,"tier 0");
        check(SlayerContractRules.validateStart(true,4,false,true,200)==INVALID_TIER,"tier 4");
        check(SlayerContractRules.validateStart(true,1,true,true,200)==ALREADY_ACTIVE,"active blocks charge");
        check(SlayerContractRules.validateStart(true,1,false,false,200)==NO_PERMISSION,"rank prevents charge");
        check(SlayerContractRules.validateStart(true,1,false,true,-1)==BAD_PRICE,"unconfigured cost");
        check(SlayerContractRules.validateStart(true,1,false,true,1_000_000_001L)==BAD_PRICE,"overflow cost");
        check(SlayerContractRules.validateStart(true,1,false,true,200)==NONE,"valid revenant rank 1");
        check(SlayerContractRules.validateStart(true,3,false,true,950)==NONE,"valid revenant rank 3");
        check(SlayerContractRules.validateCancel(false,true,200)==DISABLED,"cancel disabled");
        check(SlayerContractRules.validateCancel(true,false,200)==ALREADY_ACTIVE,"cancel inactive");
        check(SlayerContractRules.validateCancel(true,true,-1)==BAD_PRICE,"missing cancellation cost");
        check(SlayerContractRules.validateCancel(true,true,200)==NONE,"valid cancellation");
        System.out.println("PASS SlayerContractSmoke: " + checks + " checks");
    }
}
