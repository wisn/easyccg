package uk.ac.ed.easyccg.syntax;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import uk.ac.ed.easyccg.syntax.Category.Slash;

public abstract class Combinator
{
  public enum RuleType {
    FA, BA, FC, BXC, FXC, BC, GFC, GBXC, GFXC, GBC, CONJ, RP, LP, NOISE, UNARY, LEXICON;

    boolean isForward() {
      switch (this) {
        case FA:
        case FC:
        case FXC:
        case GFC:
        case GFXC:
          return true;
        default:
          return false;
      }
    }
    
    boolean isApp() {
      switch (this) {
        case FA:
        case BA:
          return true;
        default:
          return false;
      }
    }

    boolean isComp() {
      switch (this) {
        case FC:
        case BXC:
        case FXC:
        case BC:
        case GFC:
        case GBXC:
        case GFXC:
        case GBC:
          return true;
        default:
          return false;
      }
    }

    boolean isAppOrComp1() {
      switch (this) {
        case FA:
        case BA:
        case FC:
        case BXC:
        case FXC:
        case BC:
          return true;
        default:
          return false;
      }
    }

    boolean isBackward() {
      switch (this) {
        case BA:
        case BC:
        case BXC:
        case GBC:
        case GBXC:
          return true;
        default:
          return false;
      }
    }

    boolean isComp1() {
      switch (this) {
        case FC:
        case BXC:
        case FXC:
        case BC:
          return true;
        default:
          return false;
      }
    }

    boolean isComp2() {
      switch (this) {
        case GFC:
        case GBXC:
        case GFXC:
        case GBC:
          return true;
        default:
          return false;
      }
    }

    int getDegree() {
      if (isComp1()) {
        return 1;
      } else if (isComp2()) {
        return 2;
      } else {
        return 0; // TODO assign type-raising rules a non-0 degree?
      }
    }
  }
  
  private Combinator(RuleType ruleType)
  {
    this.ruleType = ruleType;
  }

  static class RuleProduction {
    public RuleProduction(RuleType ruleType, Category result, final boolean headIsLeft)
    {
      this.ruleType = ruleType;
      this.category = result;
      this.headIsLeft = headIsLeft;
    }
    public final RuleType ruleType;
    public final Category category;
    public final boolean headIsLeft;
  }

  public abstract boolean headIsLeft(Category left, Category right);

  /**
   * The original set of combinators with some restrictions for parsing English,
   * e.g., no crossed forward composition, no harmonic backward composition,
   * certain restrictions on the categories in backward composition.
   */
  public final static Collection<Combinator> ENGLISH_COMBINATORS = new ArrayList<Combinator>(Arrays.asList(
      new ForwardApplication(),
      new BackwardApplication(),
      new ForwardComposition(RuleType.FC, Slash.FWD, Slash.FWD, Slash.FWD), // harmonic
      new BackwardComposition(RuleType.BXC, Slash.FWD, Slash.BWD, Slash.FWD, true), // crossed, categories restricted for English
      new GeneralizedForwardComposition(RuleType.GFC, Slash.FWD, Slash.FWD, Slash.FWD), // harmonic
      new GeneralizedBackwardComposition(RuleType.GBXC, Slash.FWD, Slash.BWD, Slash.FWD, true), // crossed
      new Conjunction(),
      new RemovePunctuation(false),
      new RemovePunctuationLeft()
      ));
  
  /**
   * The generic multilingual set of combinators.
   */
  public final static Collection<Combinator> GENERIC_COMBINATORS = new ArrayList<Combinator>(Arrays.asList(
      new ForwardApplication(),
      new BackwardApplication(),
      new ForwardComposition(RuleType.FC, Slash.FWD, Slash.FWD, Slash.FWD), // harmonic
      new BackwardComposition(RuleType.BXC, Slash.FWD, Slash.BWD, Slash.FWD, false), // crossed
      new ForwardComposition(RuleType.FXC, Slash.FWD, Slash.BWD, Slash.BWD), // crossed
      new BackwardComposition(RuleType.BC, Slash.BWD, Slash.BWD, Slash.BWD, false), // harmonic
      new GeneralizedForwardComposition(RuleType.GFC, Slash.FWD, Slash.FWD, Slash.FWD), // harmonic
      new GeneralizedBackwardComposition(RuleType.GBXC, Slash.FWD, Slash.BWD, Slash.FWD, false), // crossed
      new GeneralizedForwardComposition(RuleType.GFXC, Slash.FWD, Slash.BWD, Slash.BWD), // crossed
      new GeneralizedBackwardComposition(RuleType.GBC, Slash.BWD, Slash.BWD, Slash.BWD, false), // harmonic
      new Conjunction(),
      new RemovePunctuation(false),
      new RemovePunctuationLeft()
      ));
  
  public static Collection<Combinator> loadSpecialCombinators(File file) throws IOException {
    Collection<Combinator> newCombinators = new ArrayList<Combinator>();
    for (String line : Util.readFile(file)) {
      // l , S[to]\NP NP\NP
      if (line.indexOf("#") > -1) {
        line = line.substring(0, line.indexOf("#"));
      }

      line = line.trim();
      if (line.isEmpty()) {
        continue ;
      }
      
      String[] fields = line.split(" ");
      boolean headIsLeft = fields[0].equals("l");
      Category left = Category.valueOf(fields[1]);
      Category right = Category.valueOf(fields[2]);
      Category result = Category.valueOf(fields[3]);
      newCombinators.add(new SpecialCombinator(left, right, result, headIsLeft));
    }
    return newCombinators;
  }

   
  private final RuleType ruleType;
  public abstract boolean canApply(Category left, Category right);
  public abstract Category apply(Category left, Category right);
  
  /**
   * Makes sure wildcard features are correctly instantiated.
   * 
   * We want: S[X]/(S[X]\NP) and S[dcl]\NP to combine to S[dcl]. This is done by finding any wildcards that
   * need to be matched between S[X]\NP and S[dcl]\NP, and applying the substitution to S[dcl].
   */
  private static Category correctWildCardFeatures(Category toCorrect, Category match1, Category match2) {
    return toCorrect.doSubstitution(match1.getSubstitution(match2));
  }
  
  /**
   * Returns a set of rules that can be applied to a pair of categories.
   */
  static Collection<RuleProduction> getRules(Category left, Category right, Collection<Combinator> rules) {
    Collection<RuleProduction> result = new ArrayList<RuleProduction>(2);
    for (Combinator c : rules) {
      if (c.canApply(left, right)) {
        result.add(new RuleProduction(c.ruleType, c.apply(left, right), c.headIsLeft(left, right)));
      }
    }
    
    return result;
  }
  
  private static class Conjunction extends Combinator {

    private Conjunction()
    {
      super(RuleType.CONJ);
    }

    @Override
    public boolean canApply(Category left, Category right)
    {
      if (Category.valueOf("NP\\NP").matches(right)) {
        // C&C evaluation script doesn't let you do this, for some reason.
        return false; 
      }
      
      return (left == Category.CONJ || left == Category.COMMA || left == Category.SEMICOLON)
             && !right.isPunctuation() // Don't start making weird ,\, categories...
             && !right.isTypeRaised()  // Improves coverage of C&C evaluation script. Categories can just conjoin first, then type-raise. 
      
             // Blocks noun conjunctions, which should normally be NP conjunctions. 
             // In a better world, conjunctions would have categories like (NP\NP/NP.
             // Doesn't affect F-scopes, but makes output semantically nicer.
             && !(!right.isFunctor() && right.getType().equals("N"));  
      
    }

    @Override
    public Category apply(Category left, Category right)
    {
      return Category.make(right, Slash.BWD, right);
    }

    @Override
    public boolean headIsLeft(Category left, Category right)
    {
      return false;
    }
  }
  
  private static class RemovePunctuation extends Combinator {
    private final boolean punctuationIsLeft;
    private RemovePunctuation(boolean punctuationIsLeft)
    {
      super(RuleType.RP);
      this.punctuationIsLeft = punctuationIsLeft;
    }
    
    @Override
    public boolean canApply(Category left, Category right)
    {
      return punctuationIsLeft ? left.isPunctuation() : 
              right.isPunctuation() && !Category.N.matches(left); // Disallow punctuation combining with nouns, to avoid getting NPs like "Barack Obama ." 
    }

    @Override
    public Category apply(Category left, Category right)
    {
      return punctuationIsLeft ? right : left;
    }

    @Override
    public boolean headIsLeft(Category left, Category right)
    {
      return !punctuationIsLeft;
    }
  }
  
  /**
   * Open Brackets and Quotations
   */
  private static class RemovePunctuationLeft extends Combinator {
    private RemovePunctuationLeft()
    {
      super(RuleType.LP);
    }
    
    @Override
    public boolean canApply(Category left, Category right)
    {
      return left == Category.LQU || left == Category.LRB;
    }

    @Override
    public Category apply(Category left, Category right)
    {
      return right;
    }

    @Override
    public boolean headIsLeft(Category left, Category right)
    {
      return false;
    }
  }
  
  private static class SpecialCombinator extends Combinator {
    private final Category left;
    private final Category right;
    private final Category result;
    private final boolean headIsLeft;
    
    private SpecialCombinator(Category left, Category right, Category result, boolean headIsLeft)
    {
      super(RuleType.NOISE);
      this.left = left;
      this.right = right;
      this.result = result;
      this.headIsLeft = headIsLeft;
    }
    
    @Override
    public boolean canApply(Category left, Category right)
    {
      return this.left.matches(left) && this.right.matches(right);
    }

    @Override
    public Category apply(Category left, Category right)
    {
      return result;
    }

    @Override
    public boolean headIsLeft(Category left, Category right)
    {
      return headIsLeft;
    }
  }
    
  private static class ForwardApplication extends Combinator {
    private ForwardApplication()
    {
      super(RuleType.FA);
    }
    
    @Override
    public boolean canApply(Category left, Category right)
    {
      return left.isFunctor() && left.getSlash() == Slash.FWD && left.getRight().matches(right);
    }

    @Override
    public Category apply(Category left, Category right)
    {
      if (left.isModifier()) return right;
      
      Category result = left.getLeft();
      
      result = correctWildCardFeatures(result, left.getRight(), right);

      return result;
    }

    @Override
    public boolean headIsLeft(Category left, Category right)
    {
      if (left.isModifier() || left.isTypeRaised()) return false;
      return true;
    }
  }
  
  private static class BackwardApplication extends Combinator {
    private BackwardApplication()
    {
      super(RuleType.BA);      
    }
    
    @Override
    public boolean canApply(Category left, Category right)
    {
      return right.isFunctor() && right.getSlash() == Slash.BWD && right.getRight().matches(left);
    }

    @Override
    public Category apply(Category left, Category right)
    {
      Category result;
      if (right.isModifier()) {
        result = left;
      } else {
        result = right.getLeft();
      }
      
      return correctWildCardFeatures(result, right.getRight(), left);
    }

    @Override
    public boolean headIsLeft(Category left, Category right)
    {
      if (right.isModifier() || right.isTypeRaised()) return true;
      return false;
    }
  }
  
  private static class ForwardComposition extends Combinator {
    private final Slash leftSlash;
    private final Slash rightSlash;
    private final Slash resultSlash;

    private ForwardComposition(RuleType ruleType, Slash left, Slash right, Slash result)
    {
      super(ruleType);
      this.leftSlash = left;
      this.rightSlash = right;
      this.resultSlash = result;
    }
    
    @Override
    public boolean canApply(Category left, Category right)
    {
      return left.isFunctor() && right.isFunctor() && left.getRight().matches(right.getLeft()) && left.getSlash() == leftSlash && right.getSlash() == rightSlash;
    }

    @Override
    public Category apply(Category left, Category right)
    {
      Category result;
      if (left.isModifier()) {
        result = right;
      } else {
        result = Category.make(left.getLeft(), resultSlash, right.getRight());
      }
      
      return correctWildCardFeatures(result, right.getLeft(), left.getRight());
    }

    @Override
    public boolean headIsLeft(Category left, Category right)
    {
      if (left.isModifier() || left.isTypeRaised()) return false;
      return true;
    }
  }
  
  private static class GeneralizedForwardComposition extends Combinator {
    private final Slash leftSlash;
    private final Slash rightSlash;
    private final Slash resultSlash;

    private GeneralizedForwardComposition(RuleType ruleType, Slash left, Slash right, Slash result)
    {
      super(ruleType);
      this.leftSlash = left;
      this.rightSlash = right;
      this.resultSlash = result;
    }
    
    @Override
    public boolean canApply(Category left, Category right)
    {
      if (left.isFunctor() && right.isFunctor() && right.getLeft().isFunctor()) {
        Category rightLeft = right.getLeft();
        return  left.getRight().matches(rightLeft.getLeft()) && left.getSlash() == leftSlash && rightLeft.getSlash() == rightSlash;
      } else {
        return false;
      }      
    }

    @Override
    public Category apply(Category left, Category right)
    {
      if (left.isModifier()) return right;
      
      Category rightLeft = right.getLeft();

      Category result = Category.make(Category.make(left.getLeft(), resultSlash, rightLeft.getRight()), right.getSlash(), right.getRight());

      result = correctWildCardFeatures(result, rightLeft.getLeft(), left.getRight());
      return result;
    }

    @Override
    public boolean headIsLeft(Category left, Category right)
    {
      if (left.isModifier() || left.isTypeRaised()) return false;
      return true;
    }
  }
  
  private static class GeneralizedBackwardComposition extends Combinator {
    private final Slash leftSlash;
    private final Slash rightSlash;
    private final Slash resultSlash;
    private final boolean english;

    private GeneralizedBackwardComposition(RuleType ruleType, Slash left, Slash right, Slash result, boolean english)
    {
      super(ruleType);
      this.leftSlash = left;
      this.rightSlash = right;
      this.resultSlash = result;
      this.english = english;
    }
    
    @Override
    public boolean canApply(Category left, Category right)
    {
      if (left.isFunctor() && right.isFunctor() && left.getLeft().isFunctor()) {
        Category leftLeft = left.getLeft();
        return  right.getRight().matches(leftLeft.getLeft()) && leftLeft.getSlash() == leftSlash && right.getSlash() == rightSlash
                && !(english && left.getLeft().isNounOrNP()); // Additional constraint from Steedman (2000)
      } else {
        return false;
      }      
    }

    @Override
    public Category apply(Category left, Category right)
    {
      if (right.isModifier()) return left;
      
      Category leftLeft = left.getLeft();

      Category result = Category.make(Category.make(right.getLeft(), resultSlash, leftLeft.getRight()), left.getSlash(), left.getRight());

      result = correctWildCardFeatures(result, leftLeft.getLeft(), right.getRight());
      return result;
    }

    @Override
    public boolean headIsLeft(Category left, Category right)
    {
      if (right.isModifier() || right.isTypeRaised()) return true;
      return false;
    }
  }  
  
  private static class BackwardComposition extends Combinator {
    private final Slash leftSlash;
    private final Slash rightSlash;
    private final Slash resultSlash;
    private final boolean english;

    private BackwardComposition(RuleType ruleType, Slash left, Slash right, Slash result, boolean english)
    {
      super(ruleType);
      this.leftSlash = left;
      this.rightSlash = right;
      this.resultSlash = result;
      this.english = english;
    }
    
    @Override
    public boolean canApply(Category left, Category right)
    {
      return left.isFunctor() && 
             right.isFunctor() && 
             right.getRight().matches(left.getLeft()) && 
             left.getSlash() == leftSlash && right.getSlash() == rightSlash &&
             !(english && left.getLeft().isNounOrNP()); // Additional constraint from Steedman (2000)
    }

    @Override
    public Category apply(Category left, Category right)
    {
      Category result;
      if (right.isModifier()) {
        result = left;
      } else {
        result = Category.make(right.getLeft(), resultSlash, left.getRight());
      }

      return result.doSubstitution(left.getLeft().getSubstitution(right.getRight()));
    }

    @Override
    public boolean headIsLeft(Category left, Category right)
    {
      if (right.isModifier() || right.isTypeRaised()) return true;
      return false;
    }
  }
  
  public RuleType getRuleType()
  {
    return ruleType;
  }
}
