clear; clc;

% Modelo para G(s) = 144 / (s^2 + 7.2 s + 144)
rootDir = fileparts(mfilename('fullpath'));
outDir = fullfile(rootDir, 'simulink', 'gs_response');
if ~exist(outDir, 'dir')
    mkdir(outDir);
end

modelName = 'gs_step_response';
modelFile = fullfile(outDir, [modelName '.slx']);

if bdIsLoaded(modelName)
    close_system(modelName, 0);
end
if exist(modelFile, 'file')
    delete(modelFile);
end

new_system(modelName);
set_param(modelName, ...
    'Location', [80 100 1050 600], ...
    'StopTime', '5', ...
    'SolverType', 'Variable-step', ...
    'Solver', 'ode45', ...
    'MaxStep', '0.005');

% Los coeficientes quedan guardados dentro del archivo SLX.
modelWorkspace = get_param(modelName, 'ModelWorkspace');
assignin(modelWorkspace, 'Gs_num', 144);
assignin(modelWorkspace, 'Gs_den', [1 7.2 144]);

add_block('simulink/Sources/Step', [modelName '/Entrada escalon'], ...
    'Time', '0', ...
    'Before', '0', ...
    'After', '1', ...
    'Position', [70 155 110 185]);

add_block('simulink/Continuous/Transfer Fcn', [modelName '/Gs'], ...
    'Numerator', 'Gs_num', ...
    'Denominator', 'Gs_den', ...
    'Position', [245 135 445 205]);
set_param([modelName '/Gs'], ...
    'AttributesFormatString', '144/(s^2 + 7.2s + 144)');

add_block('simulink/Signal Routing/Mux', [modelName '/Mux entrada y salida'], ...
    'Inputs', '2', ...
    'Position', [550 115 555 225]);

add_block('simulink/Sinks/Scope', [modelName '/Scope respuesta'], ...
    'Position', [670 145 730 195]);

add_block('simulink/Sinks/To Workspace', [modelName '/Salida al workspace'], ...
    'VariableName', 'y_gs', ...
    'SaveFormat', 'Structure With Time', ...
    'Position', [485 285 625 315]);

add_block('simulink/Sinks/Out1', [modelName '/Salida y(t)'], ...
    'Position', [700 285 730 305]);

add_line(modelName, 'Entrada escalon/1', 'Gs/1', 'autorouting', 'on');
add_line(modelName, 'Entrada escalon/1', 'Mux entrada y salida/1', 'autorouting', 'on');
add_line(modelName, 'Gs/1', 'Mux entrada y salida/2', 'autorouting', 'on');
add_line(modelName, 'Mux entrada y salida/1', 'Scope respuesta/1', 'autorouting', 'on');
add_line(modelName, 'Gs/1', 'Salida al workspace/1', 'autorouting', 'on');
add_line(modelName, 'Gs/1', 'Salida y(t)/1', 'autorouting', 'on');

annotationText = sprintf(['G(s) = 144 / (s^2 + 7.2s + 144)\n' ...
    'Entrada: escalon unitario   |   Tiempo: 5 s\n' ...
    'El Scope compara la entrada r(t) con la salida y(t).']);
add_block('built-in/Note', [modelName '/Descripcion'], ...
    'Position', [70 350 445 430], ...
    'Text', annotationText, ...
    'FontSize', '12');

set_param(modelName, 'SimulationCommand', 'update');
save_system(modelName, modelFile);
print(['-s' modelName], '-dpng', '-r160', ...
    fullfile(outDir, 'gs_step_response_model.png'));

% Ejecuta una simulacion de comprobacion y crea una grafica de referencia.
simOut = sim(modelName, 'ReturnWorkspaceOutputs', 'on');
y = simOut.get('y_gs');

fig = figure('Visible', 'off', 'Color', 'white');
plot(y.time, ones(size(y.time)), '--', 'LineWidth', 1.2); hold on;
plot(y.time, y.signals.values, 'LineWidth', 1.8);
grid on;
xlabel('Tiempo (s)');
ylabel('Amplitud');
title('Respuesta de G(s) a un escalon unitario');
legend('Entrada r(t)', 'Salida y(t)', 'Location', 'best');
xlim([0 5]);
exportgraphics(fig, fullfile(outDir, 'gs_step_response.png'), 'Resolution', 160);
close(fig);

close_system(modelName, 0);
fprintf('Modelo creado: %s\n', modelFile);
