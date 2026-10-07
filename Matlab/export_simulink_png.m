rootDir = fileparts(mfilename('fullpath'));
outDir = fullfile(rootDir,'simulink','output');
models = {'model_01_real_system','model_02_continuous','model_03_discrete'};
for i = 1:numel(models)
    model = models{i};
    load_system(fullfile(outDir,[model '.slx']));
    set_param(model,'ZoomFactor','FitSystem');
    print(['-s' model],'-dpng','-r180',fullfile(outDir,[model '.png']));
    close_system(model,0);
end
