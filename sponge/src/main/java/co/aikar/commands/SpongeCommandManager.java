/*
 * Copyright (c) 2016-2017 Daniel Ennis (Aikar) - MIT License
 *
 *  Permission is hereby granted, free of charge, to any person obtaining
 *  a copy of this software and associated documentation files (the
 *  "Software"), to deal in the Software without restriction, including
 *  without limitation the rights to use, copy, modify, merge, publish,
 *  distribute, sublicense, and/or sell copies of the Software, and to
 *  permit persons to whom the Software is furnished to do so, subject to
 *  the following conditions:
 *
 *  The above copyright notice and this permission notice shall be
 *  included in all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 *  EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 *  MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 *  NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 *  LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 *  OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 *  WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package co.aikar.commands;

import co.aikar.commands.apachecommonslang.ApacheCommonsExceptionUtil;
import co.aikar.timings.Timing;
import co.aikar.timings.Timings;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.text.format.TextColor;
import org.spongepowered.api.text.format.TextColors;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@SuppressWarnings("WeakerAccess")
public class SpongeCommandManager extends CommandManager<
        CommandSource,
        SpongeCommandIssuer,
        TextColor,
        SpongeMessageFormatter,
        SpongeCommandExecutionContext,
        SpongeConditionContext
    > {

    protected final PluginContainer plugin;
    protected Map<String, SpongeRootCommand> registeredCommands = new HashMap<>();
    protected SpongeCommandContexts contexts;
    protected SpongeCommandCompletions completions;
    private Timing commandTiming;
    protected SpongeLocales locales;

    public SpongeCommandManager(PluginContainer plugin) {
        this.plugin = plugin;
        String pluginName = "acf-" + plugin.getName();
        getLocales().addMessageBundles("acf-minecraft", pluginName, pluginName.toLowerCase(Locale.ENGLISH));
        this.commandTiming = Timings.of(plugin, "Commands");

        this.formatters.put(MessageType.ERROR, defaultFormatter = new SpongeMessageFormatter(TextColors.RED));
        this.formatters.put(MessageType.SYNTAX, new SpongeMessageFormatter(TextColors.RED));
        this.formatters.put(MessageType.INFO, new SpongeMessageFormatter(TextColors.GRAY));
        this.formatters.put(MessageType.HELP, new SpongeMessageFormatter(TextColors.AQUA, TextColors.WHITE, TextColors.DARK_GRAY, TextColors.GRAY, TextColors.RED));
        getLocales(); // auto load locales

        this.validNamePredicate = ACFSpongeUtil::isValidName;

        Sponge.getEventManager().registerListeners(plugin, new ACFSpongeListener(this));

        //TODO more default dependencies for sponge
        registerDependency(plugin.getClass(), plugin);
    }

    public PluginContainer getPlugin() {
        return plugin;
    }

    @Override
    public boolean isCommandIssuer(Class<?> type) {
        return CommandSource.class.isAssignableFrom(type);
    }

    @Override
    public synchronized CommandContexts<SpongeCommandExecutionContext> getCommandContexts() {
        if (this.contexts == null) {
            this.contexts = new SpongeCommandContexts(this);
        }
        return contexts;
    }

    @Override
    public synchronized CommandCompletions<SpongeCommandCompletionContext> getCommandCompletions() {
        if (this.completions == null) {
            this.completions = new SpongeCommandCompletions(this);
        }
        return completions;
    }

    @Override
    public SpongeLocales getLocales() {
        if (this.locales == null) {
            this.locales = new SpongeLocales(this);
            this.locales.loadLanguages();
        }
        return locales;
    }

    @Override
    public boolean hasRegisteredCommands() {
        return !registeredCommands.isEmpty();
    }

    @Override
    public void registerCommand(BaseCommand command) {
        command.onRegister(this);

        for (Map.Entry<String, RootCommand> entry : command.registeredCommands.entrySet()) {
            String commandName = entry.getKey().toLowerCase(Locale.ENGLISH);
            SpongeRootCommand spongeCommand = (SpongeRootCommand) entry.getValue();
            if (!spongeCommand.isRegistered) {
                Sponge.getCommandManager().register(this.plugin, spongeCommand, commandName);
            }
            spongeCommand.isRegistered = true;
            registeredCommands.put(commandName, spongeCommand);
        }
    }

    public Timing createTiming(final String name) {
        return Timings.of(this.plugin, name, this.commandTiming);
    }

    @Override
    public RootCommand createRootCommand(String cmd) {
        return new SpongeRootCommand(this, cmd);
    }
    
    @Override
    public Collection<RootCommand> getRegisteredRootCommands() {
        return Collections.unmodifiableCollection(registeredCommands.values());
    }

    @Override
    public SpongeCommandIssuer getCommandIssuer(Object issuer) {
        if (!(issuer instanceof CommandSource)) {
            throw new IllegalArgumentException(issuer.getClass().getName() + " is not a Command Issuer.");
        }
        return new SpongeCommandIssuer(this, (CommandSource) issuer);
    }

    @Override
    public SpongeCommandExecutionContext createCommandContext(RegisteredCommand command, CommandParameter parameter, CommandIssuer sender, List<String> args, int i, Map<String, Object> passedArgs) {
        return new SpongeCommandExecutionContext(command, parameter, (SpongeCommandIssuer) sender, args, i, passedArgs);
    }

    @Override
    public CommandCompletionContext createCompletionContext(RegisteredCommand command, CommandIssuer sender, String input, String config, String[] args) {
        return new SpongeCommandCompletionContext(command, (SpongeCommandIssuer) sender, input, config, args);
    }

    @Override
    public RegisteredCommand createRegisteredCommand(BaseCommand command, String cmdName, Method method, String prefSubCommand) {
        return new SpongeRegisteredCommand(command, cmdName, method, prefSubCommand);
    }

    @Override
    public void log(final LogLevel level, final String message, final Throwable throwable) {
        Logger logger = this.plugin.getLogger();
        switch(level) {
            case INFO:
                logger.info(LogLevel.LOG_PREFIX + message);
                if (throwable != null) {
                    for (String line : ACFPatterns.NEWLINE.split(ApacheCommonsExceptionUtil.getFullStackTrace(throwable))) {
                        logger.info(LogLevel.LOG_PREFIX + line);
                    }
                }
                return;
            case ERROR:
                logger.error(LogLevel.LOG_PREFIX + message);
                if (throwable != null) {
                    for (String line : ACFPatterns.NEWLINE.split(ApacheCommonsExceptionUtil.getFullStackTrace(throwable))) {
                        logger.error(LogLevel.LOG_PREFIX + line);
                    }
                }
        }
    }

    @Override
    CommandOperationContext createCommandOperationContext(BaseCommand command, CommandIssuer issuer, String commandLabel, String[] args, boolean isAsync) {
        return new SpongeCommandOperationContext(
                this,
                issuer,
                command,
                commandLabel,
                args,
                isAsync
        );
    }

    @Override
    public SpongeConditionContext createConditionContext(CommandIssuer issuer, String config) {
        return new SpongeConditionContext((SpongeCommandIssuer) issuer, config);
    }

    @Override
    public String getCommandPrefix(CommandIssuer issuer) {
        return issuer.isPlayer() ? "/" : "";
    }
}
