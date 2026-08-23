package com.ptcrys.fpsmatch.core.minimap.model;

import java.util.List;
import java.util.Objects;

public record AffineFit(
        AffineTransform2D transform,
        double rmsResidual,
        double maxResidual,
        int controlPointCount
) {
    private static final double RELATIVE_RANK_TOLERANCE = 1.0e-10;

    public AffineFit {
        Objects.requireNonNull(transform, "transform");
        if (!Double.isFinite(rmsResidual) || rmsResidual < 0
                || !Double.isFinite(maxResidual) || maxResidual < 0) {
            throw new IllegalArgumentException("Affine residuals must be finite and non-negative");
        }
        if (controlPointCount < 3) {
            throw new IllegalArgumentException("An affine fit requires at least three control points");
        }
    }

    public boolean hasResidualDegreesOfFreedom() {
        return controlPointCount > 3;
    }

    public static AffineFit fit(List<ControlPoint> controlPoints, boolean allowMirror) {
        Objects.requireNonNull(controlPoints, "controlPoints");
        if (controlPoints.size() < 3) {
            throw new IllegalArgumentException("At least three control points are required");
        }
        List<ControlPoint> points = List.copyOf(controlPoints);
        points.forEach(Objects::requireNonNull);

        double meanX = incrementalMean(points, true);
        double meanZ = incrementalMean(points, false);
        double scaleX = points.stream()
                .mapToDouble(point -> Math.abs(point.world().x() - meanX))
                .max().orElseThrow();
        double scaleZ = points.stream()
                .mapToDouble(point -> Math.abs(point.world().z() - meanZ))
                .max().orElseThrow();
        if (!Double.isFinite(scaleX) || !Double.isFinite(scaleZ) || scaleX == 0.0 || scaleZ == 0.0) {
            throw new IllegalArgumentException("Control points are collinear");
        }

        double[][] design = new double[points.size()][3];
        double[] targetU = new double[points.size()];
        double[] targetV = new double[points.size()];
        for (int index = 0; index < points.size(); index++) {
            ControlPoint point = points.get(index);
            design[index][0] = (point.world().x() - meanX) / scaleX;
            design[index][1] = (point.world().z() - meanZ) / scaleZ;
            design[index][2] = 1.0;
            targetU[index] = point.canvas().u();
            targetV[index] = point.canvas().v();
        }

        double[] normalizedU = solveLeastSquares(design, targetU);
        double[] normalizedV = solveLeastSquares(design, targetV);
        double m00 = normalizedU[0] / scaleX;
        double m01 = normalizedU[1] / scaleZ;
        double m10 = normalizedV[0] / scaleX;
        double m11 = normalizedV[1] / scaleZ;
        AffineTransform2D transform = new AffineTransform2D(
                m00,
                m01,
                normalizedU[2] - m00 * meanX - m01 * meanZ,
                m10,
                m11,
                normalizedV[2] - m10 * meanX - m11 * meanZ
        );
        if (transform.isMirrored() && !allowMirror) {
            throw new IllegalArgumentException("Mirrored affine transforms require explicit permission");
        }

        double sumSquared = 0;
        double maxResidual = 0;
        for (ControlPoint point : points) {
            CanvasPoint actual = transform.transform(point.world());
            double residual = Math.hypot(
                    actual.u() - point.canvas().u(),
                    actual.v() - point.canvas().v()
            );
            sumSquared += residual * residual;
            maxResidual = Math.max(maxResidual, residual);
        }
        return new AffineFit(transform, Math.sqrt(sumSquared / points.size()), maxResidual, points.size());
    }

    private static double incrementalMean(List<ControlPoint> points, boolean xAxis) {
        double mean = 0;
        for (int index = 0; index < points.size(); index++) {
            double value = xAxis ? points.get(index).world().x() : points.get(index).world().z();
            mean += (value - mean) / (index + 1);
            if (!Double.isFinite(mean)) {
                throw new IllegalArgumentException("Control point coordinates are numerically unsafe");
            }
        }
        return mean;
    }

    private static double[] solveLeastSquares(double[][] matrix, double[] target) {
        int rows = matrix.length;
        int columns = 3;
        double[][] qr = new double[rows][columns];
        for (int row = 0; row < rows; row++) {
            System.arraycopy(matrix[row], 0, qr[row], 0, columns);
        }
        double[] solution = target.clone();
        double[] diagonal = new double[columns];
        int[] permutation = new int[]{0, 1, 2};
        double leadingDiagonal = 0;

        for (int column = 0; column < columns; column++) {
            int pivot = column;
            double pivotNorm = columnNorm(qr, column, column);
            for (int candidate = column + 1; candidate < columns; candidate++) {
                double candidateNorm = columnNorm(qr, candidate, column);
                if (candidateNorm > pivotNorm
                        || (candidateNorm == pivotNorm && permutation[candidate] < permutation[pivot])) {
                    pivot = candidate;
                    pivotNorm = candidateNorm;
                }
            }
            if (pivot != column) {
                swapColumns(qr, pivot, column);
                int old = permutation[pivot];
                permutation[pivot] = permutation[column];
                permutation[column] = old;
            }

            double norm = pivotNorm;
            if (!Double.isFinite(norm) || norm == 0.0) {
                throw new IllegalArgumentException("Control points do not define an invertible affine transform");
            }
            if (qr[column][column] < 0) {
                norm = -norm;
            }
            for (int row = column; row < rows; row++) {
                qr[row][column] /= norm;
            }
            qr[column][column] += 1.0;

            for (int next = column + 1; next < columns; next++) {
                double projection = 0;
                for (int row = column; row < rows; row++) {
                    projection += qr[row][column] * qr[row][next];
                }
                projection = -projection / qr[column][column];
                for (int row = column; row < rows; row++) {
                    qr[row][next] += projection * qr[row][column];
                }
            }

            double projection = 0;
            for (int row = column; row < rows; row++) {
                projection += qr[row][column] * solution[row];
            }
            projection = -projection / qr[column][column];
            for (int row = column; row < rows; row++) {
                solution[row] += projection * qr[row][column];
            }
            diagonal[column] = -norm;
            if (column == 0) {
                leadingDiagonal = Math.abs(diagonal[column]);
            } else if (Math.abs(diagonal[column]) <= leadingDiagonal * RELATIVE_RANK_TOLERANCE) {
                throw new IllegalArgumentException("Control points are collinear or numerically singular");
            }
        }

        for (int row = columns - 1; row >= 0; row--) {
            if (Math.abs(diagonal[row]) <= leadingDiagonal * RELATIVE_RANK_TOLERANCE) {
                throw new IllegalArgumentException("Control points are collinear or numerically singular");
            }
            solution[row] /= diagonal[row];
            for (int previous = 0; previous < row; previous++) {
                solution[previous] -= solution[row] * qr[previous][row];
            }
        }
        double[] unpermuted = new double[columns];
        for (int column = 0; column < columns; column++) {
            unpermuted[permutation[column]] = solution[column];
        }
        return unpermuted;
    }

    private static double columnNorm(double[][] matrix, int column, int startRow) {
        double norm = 0;
        for (int row = startRow; row < matrix.length; row++) {
            norm = Math.hypot(norm, matrix[row][column]);
        }
        return norm;
    }

    private static void swapColumns(double[][] matrix, int left, int right) {
        for (double[] row : matrix) {
            double value = row[left];
            row[left] = row[right];
            row[right] = value;
        }
    }
}
