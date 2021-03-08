package org.janelia.render.client.zspacing;

import ij.ImagePlus;
import ij.process.FloatProcessor;

import java.io.File;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;

import mpicbg.trakem2.transform.TransformMeshMappingWithMasks.ImageProcessorWithMasks;

import org.janelia.alignment.RenderParameters;
import org.janelia.alignment.Renderer;
import org.janelia.alignment.util.ImageProcessorCache;

/**
 * Interface (and several implementations) for loading aligned layer pixels for z position correction.
 *
 * @author Eric Trautman
 */
public interface LayerLoader {

    /**
     * @return total number of slices to load.
     */
    int getNumberOfLayers();

    /**
     * @return image processor for the specified slice index.
     */
    FloatProcessor getProcessor(final int layerIndex);

    /**
     * Simple LRU cache of loaded processors for slices.
     */
    class SimpleLeastRecentlyUsedLayerCache
            implements LayerLoader {

        private final LayerLoader loader;
        private final int maxNumberOfLayersToCache;

        private final LinkedHashMap<Integer, FloatProcessor> indexToLayerProcessor;

        public SimpleLeastRecentlyUsedLayerCache(final LayerLoader loader,
                                                 final int maxNumberOfLayersToCache) {
            this.loader = loader;
            this.maxNumberOfLayersToCache = maxNumberOfLayersToCache;
            this.indexToLayerProcessor = new LinkedHashMap<>();
        }

        @Override
        public int getNumberOfLayers() {
            return loader.getNumberOfLayers();
        }

        @Override
        public FloatProcessor getProcessor(final int layerIndex) {
            return threadSafeGet(layerIndex);
        }

        private synchronized FloatProcessor threadSafeGet(final int layerIndex) {

            FloatProcessor processor = indexToLayerProcessor.remove(layerIndex);

            if (processor == null) {

                if (indexToLayerProcessor.size() >= maxNumberOfLayersToCache) {
                    final Integer leastRecentlyUsedIndex = indexToLayerProcessor.keySet()
                            .stream()
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("cache should have at least one element"));
                    indexToLayerProcessor.remove(leastRecentlyUsedIndex);
                }

                processor = loader.getProcessor(layerIndex);
            }

            // reorder linked hash map so that most recently used is last
            indexToLayerProcessor.put(layerIndex, processor);

            return processor;
        }
    }

    class PathLayerLoader implements LayerLoader, Serializable {

        private final List<String> layerPathList;

        public PathLayerLoader(final List<String> layerPathList) {
            this.layerPathList = layerPathList;
        }

        @Override
        public int getNumberOfLayers() {
            return layerPathList.size();
        }

        @Override
        public FloatProcessor getProcessor(final int layerIndex) {
            final String layerPath = layerPathList.get(layerIndex);
            return new ImagePlus(layerPath).getProcessor().convertToFloatProcessor();
        }
    }

    class RenderLayerLoader implements LayerLoader, Serializable {

        private final String layerUrlPattern;
        private final List<Double> sortedZList;
        private final ImageProcessorCache imageProcessorCache;

        private String debugFilePattern;

        public RenderLayerLoader(final String layerUrlPattern,
                                 final List<Double> sortedZList,
                                 final ImageProcessorCache imageProcessorCache) {
            this.layerUrlPattern = layerUrlPattern;
            this.sortedZList = sortedZList;
            this.debugFilePattern = null;
            this.imageProcessorCache = imageProcessorCache;
        }

        @Override
        public int getNumberOfLayers() {
            return sortedZList.size();
        }

        @Override
        public FloatProcessor getProcessor(final int layerIndex) {

            final Double z = sortedZList.get(layerIndex);
            final String url = String.format(layerUrlPattern, z);

            final RenderParameters renderParameters = RenderParameters.loadFromUrl(url);

            final File debugFile = debugFilePattern == null ? null : new File(String.format(debugFilePattern, z));

            final ImageProcessorWithMasks imageProcessorWithMasks =
                    Renderer.renderImageProcessorWithMasks(renderParameters,
                                                           imageProcessorCache,
                                                           debugFile);

            // TODO: make sure it is ok to drop mask here
            return imageProcessorWithMasks.ip.convertToFloatProcessor();
        }

        public Double getFirstLayerZ() {
            return sortedZList.get(0);
        }

        public void setDebugFilePattern(final String debugFilePattern) {
            this.debugFilePattern = debugFilePattern;
        }
    }
}
