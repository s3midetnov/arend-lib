package org.arend.lib.meta.monad;

import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.ConcreteSourceNode;
import org.arend.ext.concrete.ResolvedApplication;
import org.arend.ext.concrete.expr.*;
import org.arend.ext.error.NameResolverError;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.reference.ConcreteUnparsedSequenceElem;
import org.arend.ext.reference.ExpressionResolver;
import org.arend.ext.reference.Fixity;
import org.arend.ext.typechecking.BaseMetaDefinition;
import org.arend.ext.typechecking.ContextData;
import org.arend.ext.typechecking.MetaResolver;
import org.arend.lib.StdExtension;
import org.arend.lib.util.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class BaseMonadMeta extends BaseMetaDefinition implements MetaResolver {
  final StdExtension ext;

  public BaseMonadMeta(StdExtension ext) {
    this.ext = ext;
  }

  @Override
  public boolean @Nullable [] argumentExplicitness() {
    return new boolean[] { false };
  }

  @Override
  public boolean allowEmptyCoclauses() {
    return true;
  }

  abstract ConcreteExpression combine(ConcreteExpression expr1, ConcreteExpression expr2, ConcreteFactory factory);

  abstract ConcreteExpression combine(ConcreteExpression expr1, ArendRef ref, ConcreteExpression expr2, ConcreteFactory factory);

  private ConcreteExpression getConcreteRepresentation(List<? extends ConcreteArgument> arguments, ConcreteSourceNode marker, ExpressionResolver resolver) {
    List<? extends ConcreteExpression> args = Utils.getArgumentList(arguments.get(0).getExpression());
    ConcreteFactory factory = ext.factory.withData(marker);
    ConcreteExpression result = args.get(args.size() - 1);
    for (int i = args.size() - 2; i >= 0; i--) {
      ConcreteExpression arg = args.get(i);
      if (arg instanceof ConcreteLetExpression let && ((ConcreteLetExpression) arg).getExpression() instanceof ConcreteIncompleteExpression) {
        result = factory.letExpr(let.isHave(), let.isStrict(), let.getClauses(), result);
      } else if (arg instanceof ConcreteLamExpression && ((ConcreteLamExpression) arg).getBody() instanceof ConcreteIncompleteExpression) {
        result = factory.lam(((ConcreteLamExpression) arg).getParameters(), result);
      } else {
        ConcreteUnparsedSequenceExpression seqExpr = arg instanceof ConcreteUnparsedSequenceExpression seqExpr2 ? seqExpr2 : null;
        ResolvedApplication resolvedApp = resolver != null && seqExpr != null ? resolver.resolveApplication(seqExpr) : null;
        if (resolvedApp != null && resolvedApp.function() instanceof ConcreteReferenceExpression refExpr && refExpr.getReferent() == ext.leftArrowRef && resolvedApp.leftElements() != null && resolvedApp.rightElements() != null && !resolvedApp.rightElements().isEmpty()) {
          ConcreteUnparsedSequenceElem leftArg = resolvedApp.leftElements().size() == 1 ? resolvedApp.leftElements().get(0) : null;
          if (!(leftArg != null && leftArg.isExplicit() && (leftArg.getFixity() == Fixity.UNKNOWN || leftArg.getFixity() == Fixity.NONFIX) && (leftArg.getExpression() instanceof ConcreteReferenceExpression || leftArg.getExpression() instanceof ConcreteHoleExpression))) {
            resolver.getErrorReporter().report(new NameResolverError("The left argument of '<-' must be a variable", resolvedApp.leftElements().size() == 1 ? resolvedApp.leftElements().get(0).getExpression() : resolvedApp.function()));
            return null;
          }
          result = combine(factory.unparsedSequence(resolvedApp.rightElements(), seqExpr.getClauses()), leftArg.getExpression() instanceof ConcreteReferenceExpression leftExpr ? leftExpr.getReferent() : null, result, factory);
        } else {
          result = combine(arg, result, factory);
        }
      }
    }
    return result;
  }

  @Override
  public @Nullable ConcreteExpression getConcreteRepresentation(@NotNull List<? extends ConcreteArgument> arguments) {
    return getConcreteRepresentation(arguments, null, null);
  }

  @Override
  public @Nullable ConcreteExpression resolvePrefix(@NotNull ExpressionResolver resolver, @NotNull ContextData contextData) {
    if (!checkContextData(contextData, resolver.getErrorReporter())) {
      return null;
    }
    if (contextData.getArguments().isEmpty() == (contextData.getCoclauses() == null)) {
      resolver.getErrorReporter().report(new NameResolverError("Expected 1 implicit argument", contextData.getMarker()));
      return null;
    }
    if (contextData.getCoclauses() != null) {
      return ext.factory.withData(contextData.getCoclauses().getData()).goal();
    }

    ConcreteExpression result = getConcreteRepresentation(contextData.getArguments(), contextData.getReferenceExpression(), resolver);
    return result == null ? null : resolver.resolve(result);
  }
}