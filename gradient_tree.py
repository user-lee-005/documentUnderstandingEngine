

import numpy as np


class TreeNode:
    def __init__(self, feature=None, threshold=None, left=None, right=None, value=None):
        self.feature = feature
        self.threshold = threshold
        self.left = left
        self.right = right
        self.value = value


class DecisionTree:
    def __init__(self, max_depth=3):
        self.max_depth = max_depth

    def train(self, X, y, depth=0):

        if depth >= self.max_depth or len(set(y)) == 1:
            return TreeNode(value=np.mean(y))

        feature, threshold = self.best_split(X, y)

        if feature is None:
            return TreeNode(value=np.mean(y))

        left_index = X[:, feature] <= threshold
        right_index = X[:, feature] > threshold

        left = self.train(
            X[left_index],
            y[left_index],
            depth + 1
        )

        right = self.train(
            X[right_index],
            y[right_index],
            depth + 1
        )

        return TreeNode(
            feature,
            threshold,
            left,
            right
        )

    def best_split(self, X, y):

        best_loss = float("inf")
        best_feature = None
        best_threshold = None

        for feature in range(X.shape[1]):

            values = np.unique(X[:, feature])

            for threshold in values:

                left = y[X[:, feature] <= threshold]
                right = y[X[:, feature] > threshold]

                if len(left) == 0 or len(right) == 0:
                    continue

                loss = (
                    np.var(left) * len(left)
                    +
                    np.var(right) * len(right)
                )

                if loss < best_loss:
                    best_loss = loss
                    best_feature = feature
                    best_threshold = threshold

        return best_feature, best_threshold


    def predict_row(self, node, row):

        if node.value is not None:
            return node.value

        if row[node.feature] <= node.threshold:
            return self.predict_row(node.left, row)

        return self.predict_row(node.right, row)


    def predict(self, X):

        return np.array([
            self.predict_row(self.root, row)
            for row in X
        ])


    def fit(self, X, y):

        self.root = self.train(X, y)



class GradientTree:

    def __init__(self, trees=5, learning_rate=0.1):
        self.trees = trees
        self.learning_rate = learning_rate
        self.models = []
        self.initial_value = 0


    
    def train(self, X, y):

        X = np.array(X)
        y = np.array(y)

        self.initial_value = np.mean(y)

        prediction = np.full(
            len(y),
            self.initial_value
        )

        for i in range(self.trees):

            gradient = y - prediction

            tree = DecisionTree(max_depth=3)

            tree.fit(
                X,
                gradient
            )

            update = tree.predict(X)

            prediction += self.learning_rate * update

            self.models.append(tree)


    
    def get_result(self, X):

        X = np.array(X)

        result = np.full(
            len(X),
            self.initial_value
        )

        for tree in self.models:

            result += (
                self.learning_rate
                *
                tree.predict(X)
            )

        return result