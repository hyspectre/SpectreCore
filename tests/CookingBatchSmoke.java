import dev.hydev.spectrecore.cooking.CookingBatch;
public class CookingBatchSmoke {
    public static void main(String[] args) {
        for (int portions = 1; portions <= 3; portions++) {
            assert CookingBatch.requiredIngredients(portions) == portions * 3;
            int accepted = 0;
            for (int i = 0; i < 3; i++) {
                assert CookingBatch.ingredientIndex(accepted, portions) == i;
                assert CookingBatch.acceptedFromStack(accepted, portions, 64) == portions;
                accepted += portions;
            }
            assert CookingBatch.readyToCook(accepted, portions);
            assert CookingBatch.ingredientIndex(accepted, portions) == -1;
            assert CookingBatch.acceptedFromStack(accepted, portions, 64) == 0;
            assert CookingBatch.visibleIngredients(accepted, portions) == 3;
        }
        assert CookingBatch.acceptedFromStack(2, 3, 64) == 1;
        assert CookingBatch.visibleIngredients(3, 3) == 1;
        assert CookingBatch.visibleIngredients(4, 3) == 2;
        assert CookingBatch.portionsFromWater(0) == 1;
        assert CookingBatch.portionsFromWater(9) == 3;
        System.out.println("OK CookingBatchSmoke");
    }
}
