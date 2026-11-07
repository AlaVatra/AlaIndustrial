package dev.alaindustrial.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import java.util.function.Predicate;

/**
 * The tooltip style's bytecode rule (MOD-958, {@code docs/standards/tooltip-style.md}), a chapter of
 * {@link ArchitectureRules} kept in its own class: that file is at the size limit of coding.md §10.
 */
@AnalyzeClasses(packages = "dev.alaindustrial", importOptions = ImportOption.DoNotIncludeTests.class)
public class TooltipStyleRules {

	/** The vanilla formatting enum a tooltip body must not name (MOD-958): colours come from roles. */
	static final String CHAT_FORMATTING = "net.minecraft.ChatFormatting";

	/**
	 * An item's or a block's tooltip names no {@code ChatFormatting} (MOD-958,
	 * {@code docs/standards/tooltip-style.md}):
	 * it declares its lines by role ({@code MachineTooltipSpec}, {@code ItemTooltip}) and the client's
	 * {@code TooltipRender} is the one place a role becomes a colour. Scoped to the tooltip bodies outside
	 * {@code client..}, so the same class may colour a chat message elsewhere.
	 *
	 * <p>Under {@link FreezingArchRule}: tooltips not migrated yet are a baseline in
	 * {@code common/src/test/archunit-store} that only shrinks, by the command on
	 * {@code ArchitectureRules.coreDoesNotDependOnBlocksOrRegistry}.
	 */
	@ArchTest
	static final ArchRule itemTooltipsUseRolesNotChatFormatting = FreezingArchRule.freeze(noClasses()
			.that().resideOutsideOfPackage("dev.alaindustrial.client..")
			.should(nameClassIn(CHAT_FORMATTING, ArchitectureRules::isTooltipBody))
			.because("a tooltip line takes its colour from its role (docs/standards/tooltip-style.md, MOD-958): "
					+ "declare it through MachineTooltipSpec or ItemTooltip; TooltipRender maps the role"));

	/**
	 * Names class {@code owner} (a field, a method, a constructor) inside the code units {@code scope} accepts.
	 * The owner is a name, not a class: the L1 lane has no Minecraft jar, and the negative control proves the
	 * condition on a stand-in.
	 */
	static ArchCondition<JavaClass> nameClassIn(String owner, Predicate<JavaCodeUnit> scope) {
		return new ArchCondition<>("name " + owner + " in a tooltip body") {
			@Override
			public void check(JavaClass item, ConditionEvents events) {
				for (JavaCodeUnit codeUnit : item.getCodeUnits()) {
					if (!scope.test(codeUnit)) {
						continue;
					}
					for (JavaAccess<?> access : codeUnit.getAccessesFromSelf()) {
						if (access.getTargetOwner().getFullName().equals(owner)) {
							events.add(SimpleConditionEvent.satisfied(item, access.getTargetOwner().getSimpleName()
									+ "." + access.getName() + " named in " + codeUnit.getFullName()
									+ " — give the line a TooltipRole"));
						}
					}
				}
			}
		};
	}

}
