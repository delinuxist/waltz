package org.finos.waltz.service.aggregate_overlay_diagram;

import org.finos.waltz.data.aggregate_overlay_diagram.AggregateOverlayDiagramDao;
import org.finos.waltz.data.aggregate_overlay_diagram.AppCostWidgetDao;
import org.finos.waltz.data.aggregate_overlay_diagram.AppCountWidgetDao;
import org.finos.waltz.data.application.ApplicationIdSelectorFactory;
import org.finos.waltz.model.IdSelectionOptions;
import org.finos.waltz.model.aggregate_overlay_diagram.AggregateOverlayDiagram;
import org.finos.waltz.model.overlay_diagram.CostWidgetDatum;
import org.finos.waltz.model.overlay_diagram.CountWidgetDatum;
import org.jooq.Record1;
import org.jooq.ResultQuery;
import org.jooq.Select;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Set;

@Service
public class AggregateOverlayDiagramService {

    public static final ApplicationIdSelectorFactory APPLICATION_ID_SELECTOR_FACTORY = new ApplicationIdSelectorFactory();

    private final AggregateOverlayDiagramDao aggregateOverlayDiagramDao;
    private AppCountWidgetDao appCountWidgetDao;
    private AppCostWidgetDao appCostWidgetDao;

    @Autowired
    public AggregateOverlayDiagramService(AggregateOverlayDiagramDao aggregateOverlayDiagramDao,
                                          AppCountWidgetDao appCountWidgetDao,
                                          AppCostWidgetDao appCostWidgetDao) {
        this.aggregateOverlayDiagramDao = aggregateOverlayDiagramDao;
        this.appCountWidgetDao = appCountWidgetDao;
        this.appCostWidgetDao = appCostWidgetDao;
    }


    public AggregateOverlayDiagram getById(Long diagramId) {
        return aggregateOverlayDiagramDao.getById(diagramId);
    }


    public Set<CountWidgetDatum> findAppCountWidgetData(Long diagramId, IdSelectionOptions appSelectionOptions, LocalDate targetStateDate) {

        Select<Record1<Long>> applicationIdSelector = APPLICATION_ID_SELECTOR_FACTORY.apply(appSelectionOptions);
        return appCountWidgetDao.findWidgetData(diagramId, applicationIdSelector, targetStateDate);
    }


    public Set<CostWidgetDatum> findAppCostWidgetData(Long diagramId, IdSelectionOptions appSelectionOptions, LocalDate targetStateDate) {

        Select<Record1<Long>> applicationIdSelector = APPLICATION_ID_SELECTOR_FACTORY.apply(appSelectionOptions);
        return appCostWidgetDao.findWidgetData(diagramId, applicationIdSelector, targetStateDate);
    }

}
